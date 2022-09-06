package annotation.qual;

import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * A @Value object that can is only immediate data, and thus
 * can be accessed on any thread.
 */
@SubtypeOf(Value.class)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface ImmediateValue
{
}
