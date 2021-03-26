package io.hyperfoil.core.handlers;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

// Based on java.util.zip.GZIPInputStream
public class GzipInflatorProcessor extends MultiProcessor implements Session.ResourceKey<GzipInflatorProcessor.InflaterResource> {
   private static final Logger log = LogManager.getLogger(GzipInflatorProcessor.class);
   private static final int FHCRC = 2;    // Header CRC
   private static final int FEXTRA = 4;    // Extra field
   private static final int FNAME = 8;    // File name
   private static final int FCOMMENT = 16;   // File comment

   private final Access encodingVar;

   public GzipInflatorProcessor(Processor[] processors, Access encodingVar) {
      super(processors);
      this.encodingVar = encodingVar;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      Session.Var var = encodingVar.getVar(session);
      InflaterResource resource = session.getResource(this);
      switch (resource.state) {
         case NOT_ENCRYPTED:
            super.process(session, data, offset, length, isLastPart);
            // intentional fallthrough
         case INVALID:
            return;
         case UNINITIALIZED:
            if (var.isSet() && "gzip".equalsIgnoreCase(var.objectValue(session).toString())) {
               resource.state = State.FIRST_4_BYTES;
               // make sure we're starting clear
               resource.inflater.reset();
            } else {
               resource.state = State.NOT_ENCRYPTED;
               return;
            }
      }
      resource.process(session, data, offset, length);
   }

   @Override
   public void reserve(Session session) {
      super.reserve(session);
      session.declareResource(this, InflaterResource::new);
   }

   public class InflaterResource implements Session.Resource {
      private final Inflater inflater = new Inflater(true);
      private State state = State.UNINITIALIZED;
      private final byte[] buf = new byte[512];
      private int bufSize = 0;
      private final ByteBuf output;
      private final ByteBuffer nioOutput;

      private InflaterResource() {
         output = ByteBufAllocator.DEFAULT.buffer(512);
         output.writerIndex(output.capacity());
         assert output.nioBufferCount() == 1;
         nioOutput = output.nioBuffer();
      }

      public void process(Session session, ByteBuf data, int offset, int length) {
         int read;
         while (length > 0) {
            switch (state) {
               case INVALID:
                  return;
               case FIRST_4_BYTES:
                  read = Math.min(length, 4 - bufSize);
                  data.getBytes(offset, buf, bufSize, read);
                  bufSize += read;
                  length -= read;
                  offset += read;
                  if (bufSize >= 4) {
                     // verify magic
                     if (Byte.toUnsignedInt(buf[0]) != 0x1F || Byte.toUnsignedInt(buf[1]) != 0x8B) {
                        log.error("#{} Invalid magic bytes at the beginning of the stream", session.uniqueId());
                        invalidate(session);
                     } else if (Byte.toUnsignedInt(buf[2]) != 8) {
                        log.error("#{} Invalid compression method", session.uniqueId());
                        invalidate(session);
                     } else {
                        state = State.SKIP_6_BYTES;
                        bufSize = 0;
                     }
                  }
                  break;
               case SKIP_6_BYTES:
                  read = Math.min(length, 6 - bufSize);
                  bufSize += read;
                  offset += read;
                  length -= read;
                  if (bufSize >= 6) {
                     state = State.CHECK_EXTRA_FIELDS;
                     bufSize = 0;
                  }
                  break;
               case CHECK_EXTRA_FIELDS:
                  if ((Byte.toUnsignedInt(buf[3]) & FEXTRA) != 0) {
                     read = Math.min(length, 2 - bufSize);
                     data.getBytes(offset, buf, 0, read);
                     bufSize += read;
                     offset += read;
                     length -= read;
                     if (bufSize >= 2) {
                        state = State.SKIP_EXTRA_FIELDS;
                        bufSize = (Byte.toUnsignedInt(buf[1]) << 8) | Byte.toUnsignedInt(buf[0]);
                     }
                  } else {
                     state = State.SKIP_FILENAME;
                  }
                  break;
               case SKIP_EXTRA_FIELDS:
                  read = Math.min(length, bufSize);
                  offset += read;
                  length -= read;
                  bufSize -= read;
                  if (bufSize == 0) {
                     state = State.SKIP_FILENAME;
                  }
                  break;
               case SKIP_FILENAME:
                  if ((Byte.toUnsignedInt(buf[3]) & FNAME) != 0) {
                     if (skipZeroTerminated(data, offset, length)) {
                        state = State.SKIP_COMMENT;
                     }
                  } else {
                     state = State.SKIP_COMMENT;
                  }
                  break;
               case SKIP_COMMENT:
                  if ((Byte.toUnsignedInt(buf[3]) & FCOMMENT) != 0) {
                     if (skipZeroTerminated(data, offset, length)) {
                        state = State.CHECK_HEADER_CRC;
                     }
                  } else {
                     state = State.CHECK_HEADER_CRC;
                  }
                  break;
               case CHECK_HEADER_CRC:
                  if ((Byte.toUnsignedInt(buf[3]) & FHCRC) != 0) {
                     // this implementation is not checking header CRC
                     read = Math.min(length, 2 - bufSize);
                     bufSize += read;
                     offset += read;
                     length -= read;
                     if (bufSize >= 2) {
                        state = State.DATA;
                     }
                  } else {
                     state = State.DATA;
                  }
                  break;
               case DATA:
                  try {
                     int n;
                     while ((n = inflater.inflate(nioOutput)) == 0) {
                        if (inflater.needsDictionary()) {
                           log.error("#{} decompression requires a pre-set dictionary but it is not available.", session.uniqueId());
                           invalidate(session);
                           break;
                        } else if (inflater.finished()) {
                           offset -= inflater.getRemaining();
                           length += inflater.getRemaining();
                           inflater.reset();
                           GzipInflatorProcessor.super.process(session, Unpooled.EMPTY_BUFFER, 0, 0, true);
                           state = State.EOF;
                           bufSize = 8; // read trailers
                           break; // this cycle, not the switch
                        }
                        if (inflater.needsInput()) {
                           if (length == 0) {
                              break;
                           }
                           // Note: we could use nioBuffers for input, too,
                           // but that would cause allocations instead of copying the bytes.
                           read = Math.min(buf.length, length);
                           data.getBytes(offset, buf, 0, read);
                           offset += read;
                           length -= read;
                           inflater.setInput(buf, 0, read);
                        }
                     }
                     if (n != 0) {
                        nioOutput.position(0).limit(output.capacity());
                        boolean finished = inflater.finished();
                        GzipInflatorProcessor.super.process(session, output, 0, n, finished);
                        if (finished) {
                           offset -= inflater.getRemaining();
                           length += inflater.getRemaining();
                           inflater.reset();
                           state = State.EOF;
                           bufSize = 8; // read trailers
                        }
                     }
                  } catch (DataFormatException e) {
                     log.error("#{} Failed to decompress GZIPed data.", e, session.uniqueId());
                     invalidate(session);
                  }
                  break;
               case EOF:
                  read = Math.min(length, bufSize);
                  offset += read;
                  length -= read;
                  bufSize -= read;
                  if (bufSize == 0) {
                     // inflater is already reset, start a new instance
                     state = State.FIRST_4_BYTES;
                  }
                  break;
               default:
                  throw new IllegalStateException(state.toString());
            }
         }
      }

      private void invalidate(Session session) {
         Request request = session.currentRequest();
         if (request != null) {
            request.markInvalid();
         }
         state = State.INVALID;
      }

      private boolean skipZeroTerminated(ByteBuf data, int offset, int length) {
         byte b;
         do {
            b = data.getByte(offset);
            offset++;
            length--;
         } while (b != 0 && length > 0);
         return b == 0;
      }
   }

   private enum State {
      UNINITIALIZED,
      NOT_ENCRYPTED,
      INVALID,
      FIRST_4_BYTES,
      SKIP_6_BYTES,
      CHECK_EXTRA_FIELDS,
      SKIP_EXTRA_FIELDS,
      SKIP_FILENAME,
      SKIP_COMMENT,
      CHECK_HEADER_CRC,
      DATA,
      EOF,
   }

   /**
    * Decompresses a GZIP data and pipes the output to delegated processors. If the data contains multiple concatenated
    * GZIP streams it will pipe multiple decompressed objects with <code>isLastPart</code> set to true at the end of each stream.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("gzipInflator")
   public static class Builder extends MultiProcessor.Builder<Builder> implements Processor.Builder {
      private Object encodingVar;

      @Override
      public Processor build(boolean fragmented) {
         Processor[] processors = buildProcessors(fragmented);
         return new GzipInflatorProcessor(processors, SessionFactory.access(encodingVar));
      }

      /**
       * Variable used to pass header value from header handlers.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder encodingVar(Object var) {
         this.encodingVar = var;
         return this;
      }
   }
}
