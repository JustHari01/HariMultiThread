package com.axalotl.async.common.parallelised.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for mod entities that are safe for async ticking.
 * Entities from non-minecraft namespaces that do NOT have this annotation
 * will be ticked synchronously for safety.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AsyncCompatible {
}
