package edu.sjsu.moth.server.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * marks a parameter to be filled in with JSON or form data depending on the request that comes in.
 * this acts like RequestBody when JSON is received, but will also handle URL form parameters.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestObject {}
