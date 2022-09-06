package annotation.qual;

import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Created by neil on 11/01/2017.
 */
@SubtypeOf(UnknownIfValue.class)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface Value
{
}
