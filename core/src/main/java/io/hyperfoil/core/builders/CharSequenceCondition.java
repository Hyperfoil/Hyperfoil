package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiPredicate;

public interface CharSequenceCondition extends SerializableBiPredicate<Session, CharSequence> {
   class Builder<P> implements InitFromParam<Builder<P>> {
      private final P parent;
      private CharSequence value;
      private boolean caseSensitive = true;
      private String matchVar;
      private CompareMode compareMode;

      public Builder(P parent) {
         this.parent = parent;
      }

      public CharSequenceCondition build() {
         if (value == null && matchVar == null) {
            throw new BenchmarkDefinitionException("Must set one of: 'value', 'startsWith', 'endsWith' or 'matchVar'!");
         }
         if (caseSensitive) {
            if (value != null) {
               CharSequence myValue = value;
               return (s, string) -> {
                  int offset = 0, length = myValue.length();
                  switch (compareMode) {
                     case FULL:
                        length = Math.max(string.length(), length);
                        break;
                     case PREFIX:
                        break;
                     case SUFFIX:
                        offset = string.length() - length;
                        break;
                     default:
                        throw new IllegalStateException("Unexpected value: " + compareMode);
                  }
                  return Util.regionMatches(myValue, 0, string, offset, length);
               };
            } else {
               Access access = SessionFactory.access(matchVar);
               return (s, string) -> {
                  Object value = access.getObject(s);
                  if (value instanceof CharSequence) {
                     CharSequence v = (CharSequence) value;
                     return Util.regionMatches(v, 0, string, 0, Math.max(v.length(), string.length()));
                  }
                  return false;
               };
            }
         } else {
            if (value != null) {
               String myValue = value.toString();
               return (s, string) -> {
                  int offset = 0, length = myValue.length();
                  switch (compareMode) {
                     case FULL:
                        length = Math.max(string.length(), length);
                        break;
                     case PREFIX:
                        break;
                     case SUFFIX:
                        offset = string.length() - length;
                        break;
                     default:
                        throw new IllegalStateException("Unexpected value: " + compareMode);
                  }
                  return Util.regionMatchesIgnoreCase(myValue, 0, string, offset, length);
               };
            } else {
               Access access = SessionFactory.access(matchVar);
               return (s, string) -> {
                  Object value = access.getObject(s);
                  if (value instanceof CharSequence) {
                     CharSequence v = (CharSequence) value;
                     return Util.regionMatchesIgnoreCase(v, 0, string, 0, Math.max(v.length(), string.length()));
                  }
                  return false;
               };
            }
         }
      }

      public Builder<P> copy() {
         return new Builder<>(parent).value(value).caseSensitive(caseSensitive).matchVar(matchVar);
      }

      /**
       * @param param String that should be matched.
       * @return Self.
       */
      @Override
      public Builder<P> init(String param) {
         this.value = param;
         return this;
      }

      /**
       * True if the case must match, false if the check is case-insensitive.
       *
       * @param caseSensitive Boolean value.
       * @return Self.
       */
      public Builder<P> caseSensitive(boolean caseSensitive) {
         this.caseSensitive = caseSensitive;
         return this;
      }

      private void ensureNotSet() {
         if (value != null || matchVar != null) {
            throw new BenchmarkDefinitionException("Must set only one of: 'value', 'startsWith', 'endsWith' or 'matchVar'!");
         }
      }

      /**
       * Literal value the condition should match.
       *
       * @param value String.
       * @return Self.
       */
      public Builder<P> value(CharSequence value) {
         ensureNotSet();
         this.value = value;
         this.compareMode = CompareMode.FULL;
         return this;
      }

      public Builder<P> startsWith(CharSequence value) {
         this.value = value;
         this.compareMode = CompareMode.PREFIX;
         return this;
      }

      public Builder<P> endsWith(CharSequence value) {
         this.value = value;
         this.compareMode = CompareMode.SUFFIX;
         return this;
      }

      /**
       * Fetch the value from a variable.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder<P> matchVar(String var) {
         this.matchVar = var;
         return this;
      }

      public P end() {
         return parent;
      }
   }

   enum CompareMode {
      FULL,
      PREFIX,
      SUFFIX
   }
}
