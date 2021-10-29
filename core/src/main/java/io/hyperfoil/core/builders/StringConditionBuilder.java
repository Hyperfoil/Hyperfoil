package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.impl.Util;
import io.hyperfoil.function.SerializableBiPredicate;
import io.hyperfoil.function.SerializableIntPredicate;

public class StringConditionBuilder<B extends StringConditionBuilder<B, P>, P> implements InitFromParam<StringConditionBuilder<B, P>>, BuilderBase<B> {
   private final P parent;
   private CharSequence value;
   private boolean caseSensitive = true;
   private String matchVar;
   private CompareMode compareMode;
   private boolean negate;
   private LengthBuilder<B, P> length;

   public StringConditionBuilder() {
      this(null);
   }

   public StringConditionBuilder(P parent) {
      this.parent = parent;
   }

   public SerializableBiPredicate<Session, CharSequence> buildPredicate() {
      if (value == null && matchVar == null && length == null) {
         throw new BenchmarkDefinitionException("Must set one of: 'value', 'startsWith', 'endsWith', 'matchVar' or 'length'!");
      }
      SerializableBiPredicate<Session, CharSequence> predicate = contentPredicate();
      if (length != null) {
         SerializableIntPredicate lengthPredicate = length.buildPredicate();
         SerializableBiPredicate<Session, CharSequence> strLengthPredicate =
               (session, string) -> lengthPredicate.test(string == null ? 0 : string.length());
         if (predicate == null) {
            predicate = strLengthPredicate;
         } else {
            SerializableBiPredicate<Session, CharSequence> myPredicate = predicate;
            predicate = (session, string) -> strLengthPredicate.test(session, string) && myPredicate.test(session, string);
         }
      }
      if (predicate == null) {
         throw new BenchmarkDefinitionException("No condition set in string condition.");
      }
      return negate ? predicate.negate() : predicate;
   }

   private SerializableBiPredicate<Session, CharSequence> contentPredicate() {
      CompareMode myCompareMode = compareMode;
      boolean myCaseSensitive = caseSensitive;

      if (value != null) {
         CharSequence myValue = value;

         return (s, string) -> {
            int offset = 0, valueLength = myValue.length();
            switch (myCompareMode) {
               case FULL:
                  valueLength = Math.max(string.length(), valueLength);
                  break;
               case PREFIX:
                  break;
               case SUFFIX:
                  offset = string.length() - valueLength;
                  break;
               default:
                  throw new IllegalStateException("Unexpected value: " + myCompareMode);
            }
            return myCaseSensitive
                  ? Util.regionMatches(myValue, 0, string, offset, valueLength)
                  : Util.regionMatchesIgnoreCase(myValue, 0, string, offset, valueLength);
         };
      } else if (matchVar != null) {
         ReadAccess access = SessionFactory.readAccess(matchVar);
         return (s, string) -> {
            Object value = access.getObject(s);
            if (value instanceof CharSequence) {
               CharSequence v = (CharSequence) value;
               return myCaseSensitive
                     ? Util.regionMatches(v, 0, string, 0, Math.max(v.length(), string.length()))
                     : Util.regionMatchesIgnoreCase(v, 0, string, 0, Math.max(v.length(), string.length()));
            }
            return false;
         };
      } else {
         return null;
      }
   }

   /**
    * @param param String that should be matched.
    * @return Self.
    */
   @Override
   public StringConditionBuilder<B, P> init(String param) {
      ensureNotSet();
      this.value = param;
      return this;
   }

   @SuppressWarnings("unchecked")
   public B self() {
      return (B) this;
   }

   /**
    * True if the case must match, false if the check is case-insensitive.
    *
    * @param caseSensitive Boolean value.
    * @return Self.
    */
   public B caseSensitive(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
      return self();
   }

   private void ensureNotSet() {
      if (value != null || matchVar != null) {
         throw new BenchmarkDefinitionException("Must set only one of: 'value'/'equalTo', 'notEqualTo', 'startsWith', 'endsWith' or 'matchVar'!");
      }
   }

   /**
    * Literal value the string should match.
    *
    * @param value String.
    * @return Self.
    */
   public B value(CharSequence value) {
      ensureNotSet();
      this.value = value;
      this.compareMode = CompareMode.FULL;
      return self();
   }

   /**
    * Literal value the string should match (the same as {@link #value}).
    *
    * @param value String.
    * @return Self.
    */
   public B equalTo(CharSequence value) {
      return value(value);
   }

   /**
    * Value that the string must not match.
    *
    * @param value String.
    * @return Self.
    */
   public B notEqualTo(CharSequence value) {
      this.negate = true;
      return value(value);
   }

   /**
    * Prefix for the string.
    *
    * @param value String.
    * @return Self.
    */
   public B startsWith(CharSequence value) {
      ensureNotSet();
      this.value = value;
      this.compareMode = CompareMode.PREFIX;
      return self();
   }

   /**
    * Suffix for the string.
    *
    * @param value String.
    * @return Self.
    */
   public B endsWith(CharSequence value) {
      ensureNotSet();
      this.value = value;
      this.compareMode = CompareMode.SUFFIX;
      return self();
   }

   /**
    * Fetch the value from a variable.
    *
    * @param var Variable name.
    * @return Self.
    */
   public B matchVar(String var) {
      ensureNotSet();
      this.matchVar = var;
      return self();
   }

   /**
    * Invert the logic of this condition. Defaults to false.
    *
    * @param negate Use <code>true</code> if the logic should be inverted.
    * @return Self.
    */
   public B negate(boolean negate) {
      this.negate = negate;
      return self();
   }

   /**
    * Check the length of the string.
    *
    * @param exactLength String length.
    * @return Self.
    */
   public B length(int exactLength) {
      if (length == null) {
         length = new LengthBuilder<>(this);
      }
      length.equalTo(exactLength);
      return self();
   }

   /**
    * Check the length of the string.
    *
    * @return Builder.
    */
   public LengthBuilder<B, P> length() {
      if (length == null) {
         length = new LengthBuilder<>(this);
      }
      return length;
   }

   public P end() {
      return parent;
   }

   public static class LengthBuilder<B extends StringConditionBuilder<B, P>, P> extends
         IntConditionBuilder<LengthBuilder<B, P>, StringConditionBuilder<B, P>> {
      public LengthBuilder(StringConditionBuilder<B, P> parent) {
         super(parent);
      }
   }

   public enum CompareMode {
      FULL,
      PREFIX,
      SUFFIX
   }
}
