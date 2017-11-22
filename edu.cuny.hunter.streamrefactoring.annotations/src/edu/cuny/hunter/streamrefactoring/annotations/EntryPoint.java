package edu.cuny.hunter.streamrefactoring.annotations;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Purpose: set entry points for test cases. This annotation type definition is
 * for a customized annotation @EntryPoint.
 * 
 * How to use it: the annotation @EntryPoint should be written before a declared
 * method in a test case and this declared method should be an entry point of
 * the test case.
 *
 */
@Retention(CLASS)
@Target(METHOD)
public @interface EntryPoint {

}
