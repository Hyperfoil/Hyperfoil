package io.hyperfoil.core.http;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.http.CacheControl;
import io.hyperfoil.api.http.HttpCache;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.core.util.Trie;
import io.hyperfoil.util.Util;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

/**
 * This represents a browser cache = private one.
 */
public class HttpCacheImpl implements HttpCache {
   private static final Logger log = LoggerFactory.getLogger(HttpCacheImpl.class);

   // we're ignoring no-transform directive
   private static final Trie REQUEST_CACHE_CONTROL = new Trie("max-age=", "no-cache", "no-store", "max-stale=", "min-fresh=", "only-if-cached");
   // ignoring no-transform, public, private, proxy-revalidate and s-max-age as this is a private cache
   private static final Trie RESPONSE_CACHE_CONTROL = new Trie("max-age=", "no-cache", "no-store", "must-revalidate");
   private static final int MAX_AGE = 0;
   private static final int NO_CACHE = 1;
   private static final int NO_STORE = 2;
   private static final int MAX_STALE = 3;
   private static final int MIN_FRESH = 4;
   private static final int ONLY_IF_CACHED = 5;
   private static final int MUST_REVALIDATE = 3;

   private final Clock clock;
   // TODO: optimize this structure
   private final Map<CharSequence, Map<CharSequence, List<Record>>> records = new HashMap<>();

   public HttpCacheImpl(Clock clock) {
      this.clock = clock;
   }

   @Override
   public void beforeRequestHeaders(HttpRequest request) {
      switch (request.method) {
         case GET:
         case HEAD:
            break;
         default:
            // we never cache other queries
            return;
      }
      Map<CharSequence, List<Record>> authorityRecords = records.get(request.authority);
      if (authorityRecords == null) {
         return;
      }
      List<Record> pathRecords = authorityRecords.get(request.path);
      if (pathRecords == null || pathRecords.isEmpty()) {
         return;
      }
      for (int i = 0; i < pathRecords.size(); ++i) {
         request.cacheControl.matchingCached.add(pathRecords.get(i));
      }
   }

   @Override
   public void requestHeader(HttpRequest request, CharSequence header, CharSequence value) {
      if (request.method != HttpMethod.GET && request.method != HttpMethod.HEAD) {
         return;
      }
      if (HttpHeaderNames.CACHE_CONTROL.contentEqualsIgnoreCase(header)) {
         handleRequestCacheControl(request, value);
      } else if (HttpHeaderNames.PRAGMA.contentEqualsIgnoreCase(header)) {
         // We should ignore Pragma if there's Cache-Control, too, but we can't see to the future...
         if (AsciiString.contentEquals("no-cache", value)) {
            request.cacheControl.noCache = true;
         }
      } else if (HttpHeaderNames.IF_MATCH.contentEqualsIgnoreCase(header)) {
         handleIfMatch(request, value);
      } else if (HttpHeaderNames.IF_NONE_MATCH.contentEqualsIgnoreCase(header)) {
         handleIfNoneMatch(request, value);
      }
      // Note: theoretically we need all headers as these might influence the caching
      // if the cache keeps a record with 'Vary' header; for simplicity the cache
      // won't store any such records.
   }

   // This is the command commonly used with GET: return if server-version differs from local one.
   // That means that `matchingCached` should contain entries with these tags.
   private void handleIfNoneMatch(HttpRequest request, CharSequence value) {
      // We'll parse the header multiple times to avoid allocating extra colleciton
      RECORD_LOOP: for (Iterator<HttpCache.Record> iterator = request.cacheControl.matchingCached.iterator(); iterator.hasNext(); ) {
         Record record = (Record) iterator.next();
         if (record.etag == null) {
            iterator.remove();
            continue;
         }
         for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c == ' ') {
               continue;
            } else if (c == '*') {
               continue RECORD_LOOP;
            } else if (c == 'W') {
               // We'll use weak comparison so we can ignore the weakness flag
               if (++i >= value.length() || value.charAt(i) != '/') {
                  log.warn("Invalid If-None-Match: {}", value);
                  return;
               }
            } else if (c == '"') {
               int start = ++i;
               for (; i < value.length() && value.charAt(i) != '"'; ++i) ;
               int length = i - start;
               if (length == record.etag.length() && AsciiString.regionMatches(record.etag, false, 0, value, start, length)) {
                  continue RECORD_LOOP;
               }
               while (++i < value.length() && value.charAt(i) == ' ') ;
               if (i < value.length() && value.charAt(i) != ',') {
                  log.warn("Invalid If-None-Match: {}", value);
                  return;
               }
            } else {
               log.warn("Invalid If-None-Match: {}", value);
               return;
            }
         }
         // we haven't found a match
         iterator.remove();
      }
   }

   // Usually this is used with conditional modifying requests; GET if-match would return matching
   // resources from servers, so `matchingCached` should contain those records that *DONT* match.
   private void handleIfMatch(HttpRequest request, CharSequence value) {
      for (int i = 0; i < value.length(); ++i) {
         char c = value.charAt(i);
         if (c == ' ') {
            continue;
         } else if (c == '*') {
            request.cacheControl.matchingCached.clear();
            return;
         } else if (c == '"') {
            int start = ++i;
            for (; i < value.length() && value.charAt(i) != '"'; ++i);
            int length = i - start;
            List<HttpCache.Record> matchingCached = request.cacheControl.matchingCached;
            for (Iterator<HttpCache.Record> it = matchingCached.iterator(); it.hasNext(); ) {
               HttpCache.Record item = it.next();
               Record record = (Record) item;
               if (record.etag != null && !record.weakETag && length == record.etag.length() &&
                     AsciiString.regionMatches(record.etag, false, 0, value, start, length)) {
                  it.remove();
               }
            }
            while (++i < value.length() && value.charAt(i) == ' ');
            if (i < value.length() && value.charAt(i) != ',') {
               log.warn("Invalid If-Match: {}", value);
               return;
            }
         } else {
            log.warn("Invalid If-Match: {}", value);
            return;
         }
      }
   }

   private void handleRequestCacheControl(HttpRequest request, CharSequence value) {
      int maxAge = 0;
      int maxStale = 0;
      int minFresh = 0;
      Trie.State state = REQUEST_CACHE_CONTROL.newState();
      for (int i = 0; i < value.length(); ++i) {
         char c = value.charAt(i);
         if (c == ',') {
            state.reset();
            do {
               ++i;
               if (i >= value.length()) {
                  break;
               } else {
                  c = value.charAt(i);
               }
            } while (c == ' ');
            --i;
         } else {
            int pos = i + 1;
            switch (state.next((byte) (c & 0xFF))) {
               case MAX_AGE:
                  i = skipNumbers(value, pos);
                  maxAge = parseIntSaturated(value, pos, i);
                  --i;
                  break;
               case MAX_STALE:
                  i = skipNumbers(value, pos);
                  maxStale = parseIntSaturated(value, pos, i);
                  --i;
                  break;
               case MIN_FRESH:
                  i = skipNumbers(value, pos);
                  minFresh = parseIntSaturated(value, pos, i);
                  --i;
                  break;
               case NO_CACHE:
                  request.cacheControl.noCache = true;
                  break;
               case NO_STORE:
                  request.cacheControl.noStore = true;
                  break;
               case ONLY_IF_CACHED:
                  request.cacheControl.onlyIfCached = true;
                  break;
            }
         }
      }
      long now = clock.millis();
      Iterator<HttpCache.Record> it = request.cacheControl.matchingCached.iterator();
      while (it.hasNext()) {
         Record record = (Record) it.next();
         if (maxAge > 0 && now - record.date > maxAge * 1000) {
            it.remove();
         } else if ((record.mustRevalidate && now >= record.expires) || (maxStale > 0 && now - record.expires > maxStale * 1000)) {
            it.remove();
         } else if (minFresh > 0 && record.expires - now < minFresh * 1000) {
            it.remove();
         }
      }
      // When we did the filtering here we should not do it any later
      // (because that would not consider allowed stale responses)
      if (maxAge > 0 || maxStale > 0 || minFresh > 0) {
         request.cacheControl.ignoreExpires = true;
      }
   }

   @Override
   public boolean isCached(HttpRequest request, HttpRequestWriter writer) {
      if (!request.cacheControl.ignoreExpires) {
         long now = clock.millis();
         for (Iterator<HttpCache.Record> iterator = request.cacheControl.matchingCached.iterator(); iterator.hasNext(); ) {
            Record record = (Record) iterator.next();
            if (record.expires != Long.MIN_VALUE && now > record.expires) {
               iterator.remove();
            }
         }
      }
      if (request.cacheControl.matchingCached.isEmpty()) {
         if (request.cacheControl.onlyIfCached) {
            request.handlers().handleStatus(request, 504, "Request was cache-only.");
            return true;
         } else {
            return false;
         }
      } else if (request.cacheControl.noCache) {
         Record mostRecent = null;
         for (HttpCache.Record r : request.cacheControl.matchingCached) {
            Record record = (Record) r;
            if (mostRecent == null || record.date < mostRecent.date) {
               mostRecent = record;
            }
         }
         if (mostRecent.etag != null) {
            writer.putHeader(HttpHeaderNames.IF_NONE_MATCH, mostRecent.etag);
         } else if (mostRecent.lastModified > Long.MIN_VALUE) {
            writer.putHeader(HttpHeaderNames.IF_MODIFIED_SINCE, HttpUtil.formatDate(mostRecent.lastModified));
         }
         return false;
      } else {
         return true;
      }
   }

   @Override
   public void tryStore(HttpRequest request) {
      CacheControl cc = request.cacheControl;
      if (cc.noStore) {
         return;
      }
      if (cc.responseDate == Long.MIN_VALUE) {
         cc.responseDate = clock.millis() - cc.responseAge * 1000;
      }
      if (cc.responseMaxAge != 0) {
         cc.responseExpires = cc.responseDate + cc.responseMaxAge * 1000;
      }
      if (cc.responseExpires != Long.MIN_VALUE && cc.responseExpires < cc.responseDate) {
         return;
      }
      Map<CharSequence, List<Record>> authorityRecords = records.computeIfAbsent(request.authority, a -> new HashMap<>());
      List<Record> pathRecords = authorityRecords.computeIfAbsent(request.path, p -> new ArrayList<>());
      if (cc.responseEtag != null) {
         boolean weak = false;
         if (AsciiString.regionMatches(cc.responseEtag, false, 0, "W/", 0, 2)) {
            weak = true;
         }
         // Update existing record (with matching etag) or add new
         for (Record record : pathRecords) {
            if (record.etag.length() == cc.responseEtag.length() - (weak ? 4 : 2) &&
                  AsciiString.regionMatches(record.etag, false, 0, cc.responseEtag, weak ? 1 : 3, record.etag.length())) {
               record.update(cc);
               return;
            }
         }
         Record record = new Record(cc);
         pathRecords.add(record);
      } else if (cc.responseLastModified != Long.MIN_VALUE) {
         for (Record record : pathRecords) {
            if (record.lastModified > cc.responseLastModified) {
               return;
            }
         }
         Record record = pathRecords.isEmpty() ? new Record(cc) : pathRecords.get(0).update(cc);
         pathRecords.clear();
         pathRecords.add(record);
      } else {
         Record record = null;
         for (Iterator<Record> iterator = pathRecords.iterator(); iterator.hasNext(); ) {
            record = iterator.next();
            if (record.lastModified == Long.MIN_VALUE && record.etag == null) {
               iterator.remove();
            }
         }
         pathRecords.add(record == null ? new Record(cc) : record.update(cc));
      }
   }

   @Override
   public void invalidate(CharSequence authority, CharSequence path) {
      if (AsciiString.regionMatches(HttpUtil.HTTP_PREFIX, false, 0, path, 0, HttpUtil.HTTP_PREFIX.length())) {
         if (!HttpUtil.authorityMatch(path, authority, HttpUtil.HTTP_PREFIX, "80")) {
            return;
         }
         path = path.subSequence(HttpUtil.indexOf(path, HttpUtil.HTTP_PREFIX.length(), '/'), path.length());
      } else if (AsciiString.regionMatches(HttpUtil.HTTPS_PREFIX, false, 0, path, 0, HttpUtil.HTTPS_PREFIX.length())) {
         if (!HttpUtil.authorityMatch(path, authority, HttpUtil.HTTPS_PREFIX, "443")) {
            return;
         }
         path = path.subSequence(HttpUtil.indexOf(path, HttpUtil.HTTPS_PREFIX.length(), '/'), path.length());
      }
      Map<CharSequence, List<Record>> authorityRecords = records.get(authority);
      if (authorityRecords == null) {
         return;
      }
      List<Record> pathRecords = authorityRecords.get(path);
      if (pathRecords != null) {
         pathRecords.clear();
      }
   }

   @Override
   public int size() {
      return records.values().stream().flatMap(map -> map.values().stream()).mapToInt(List::size).sum();
   }

   private static int parseIntSaturated(CharSequence value, int begin, int end) {
      return (int) Math.min(Util.parseLong(value, begin, end), Integer.MAX_VALUE);
   }

   private static int skipNumbers(CharSequence value, int pos) {
      int i = pos;
      for (; i < value.length(); ++i) {
         char c = value.charAt(i);
         if (c < '0' || c > '9') {
            return i;
         }
      }
      return i;
   }

   public void responseHeader(HttpRequest request, CharSequence header, CharSequence value) {
      if (HttpHeaderNames.CACHE_CONTROL.contentEqualsIgnoreCase(header)) {
         Trie.State state = RESPONSE_CACHE_CONTROL.newState();
         for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c == ',') {
               state.reset();
               do {
                  ++i;
                  if (i >= value.length()) {
                     return;
                  } else {
                     c = value.charAt(i);
                  }
               } while (c == ' ');
               --i;
            } else {
               int pos = i + 1;
               switch (state.next((byte) (c & 0xFF))) {
                  case MAX_AGE:
                     i = skipNumbers(value, pos);
                     request.cacheControl.responseMaxAge = parseIntSaturated(value, pos, i);
                     --i;
                     break;
                  case NO_CACHE:
                     request.cacheControl.responseNoCache = true;
                     break;
                  case NO_STORE:
                     request.cacheControl.noStore = true;
                     break;
                  case MUST_REVALIDATE:
                     request.cacheControl.responseMustRevalidate = true;
                     break;
               }
            }
         }
      } else if (HttpHeaderNames.EXPIRES.contentEqualsIgnoreCase(header)) {
         request.cacheControl.responseExpires = HttpUtil.parseDate(value);
      } else if (HttpHeaderNames.AGE.contentEqualsIgnoreCase(header)) {
         request.cacheControl.responseAge = parseIntSaturated(value, 0, value.length());
      } else if (HttpHeaderNames.DATE.contentEqualsIgnoreCase(header)) {
         request.cacheControl.responseDate = HttpUtil.parseDate(value);
      } else if (HttpHeaderNames.LAST_MODIFIED.contentEqualsIgnoreCase(header)) {
         request.cacheControl.responseLastModified = HttpUtil.parseDate(value);
      } else if (HttpHeaderNames.ETAG.contentEqualsIgnoreCase(header)) {
         request.cacheControl.responseEtag = value;
      } else if (HttpHeaderNames.PRAGMA.contentEqualsIgnoreCase(header)) {
         if (AsciiString.contentEquals("no-cache", value)) {
            request.cacheControl.responseNoCache = true;
         }
      }
   }

   @Override
   public void clear() {
      for (Map<CharSequence, List<Record>> authorityRecords : records.values()) {
         for (List<Record> pathRecords : authorityRecords.values()) {
            pathRecords.clear();
         }
      }
   }

   private static class Record implements HttpCache.Record {
      long date;
      long expires;
      boolean noCache;
      boolean mustRevalidate;
      long lastModified;
      boolean weakETag;
      CharSequence etag;

      public Record(CacheControl cc) {
         this.date = cc.responseDate;
         this.expires = cc.responseExpires;
         this.noCache = cc.responseNoCache;
         this.mustRevalidate = cc.responseMustRevalidate;
         this.lastModified = cc.responseLastModified;
         this.weakETag = cc.responseEtag != null && AsciiString.regionMatches(cc.responseEtag, false, 0, "W/", 0, 2);
         this.etag = cc.responseEtag == null ? null : cc.responseEtag.subSequence(weakETag ? 3 : 1, cc.responseEtag.length() - 1);
      }

      private Record update(CacheControl cc) {
         date = Math.max(date, cc.responseDate);
         expires = Math.max(expires, cc.responseExpires);
         noCache = noCache || cc.responseNoCache;
         mustRevalidate = mustRevalidate || cc.responseMustRevalidate;
         lastModified = Math.max(lastModified, cc.responseLastModified);
         return this;
      }
   }
}
