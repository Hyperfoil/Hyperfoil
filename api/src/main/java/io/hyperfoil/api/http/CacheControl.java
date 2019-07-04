package io.hyperfoil.api.http;

import java.util.ArrayList;
import java.util.List;

public class CacheControl {
   public boolean noCache;
   public boolean noStore;
   public boolean onlyIfCached;
   public boolean ignoreExpires;
   // TODO: We're allocating iterator in this collection for removal
   // TODO: also optimize more for removal in the middle
   public List<HttpCache.Record> matchingCached = new ArrayList<>(4);

   public boolean invalidate;
   public boolean responseNoCache;
   public boolean responseMustRevalidate;
   public long responseExpires = Long.MIN_VALUE;
   public int responseAge;
   public int responseMaxAge;
   public CharSequence responseEtag;
   public long responseDate = Long.MIN_VALUE;
   public long responseLastModified = Long.MIN_VALUE;

   public void reset() {
      noCache = false;
      noStore = false;
      onlyIfCached = false;
      ignoreExpires = false;
      matchingCached.clear();

      invalidate = false;
      responseNoCache = false;
      responseMustRevalidate = false;
      responseExpires = Long.MIN_VALUE;
      responseAge = 0;
      responseMaxAge = 0;
      responseEtag = null;
      responseDate = Long.MIN_VALUE;
      responseLastModified = Long.MIN_VALUE;
   }
}
