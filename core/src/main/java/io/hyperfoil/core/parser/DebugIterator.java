package io.hyperfoil.core.parser;

import java.util.Iterator;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

class DebugIterator<T> implements Iterator<T> {
   private final Iterator<T> it;
   private String indent = "";

   DebugIterator(Iterator<T> it) {
      this.it = it;
   }

   @Override
   public boolean hasNext() {
      return it.hasNext();
   }

   @Override
   public T next() {
      T event = it.next();
      if (event instanceof MappingEndEvent || event instanceof SequenceEndEvent) {
         indent = indent.substring(2);
      }
      StackTraceElement[] stackTrace = new Exception().fillInStackTrace().getStackTrace();
      System.out.println(indent + event + " fetched from " + stackTrace[1] + "\t" + stackTrace[2] + "\t" + stackTrace[3]);
      if (event instanceof MappingStartEvent || event instanceof SequenceStartEvent) {
         indent += "| ";
      }
      return event;
   }
}
