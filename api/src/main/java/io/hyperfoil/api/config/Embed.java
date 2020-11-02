package io.hyperfoil.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For purposes of configuration embed all properties into the declaring builder.
 * <p>
 * If this annotation is used on a method it must be a public non-static no-arg method.
 * If this annotation is used on a field it must be a public final field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD })
public @interface Embed {
}
