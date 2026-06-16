package com.ikechi.studio.onwa.player.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Nullable annotation to indicate that a parameter, return value, or field can be null.
 * This is a replacement for Android's @Nullable or JetBrains annotations.
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
public @interface Nullable {
}
