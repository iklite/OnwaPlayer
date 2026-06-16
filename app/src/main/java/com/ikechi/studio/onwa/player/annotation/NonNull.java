package com.ikechi.studio.onwa.player.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom NonNull annotation to indicate that a parameter, return value, or field cannot be null.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
		ElementType.FIELD,
		ElementType.METHOD,
		ElementType.PARAMETER,
		ElementType.LOCAL_VARIABLE
		//ElementType.RETURN_TYPE
	})
public @interface NonNull {
}
