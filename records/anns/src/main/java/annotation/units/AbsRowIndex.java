package annotation.units;

import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A row index (Y position) in the VirtualGrid system.  Absolute: does not relate to any individual table,
 * only to the overall grid.  Starts at 0 on the top.
 */
@Documented
@SubtypeOf(RowOrColIndex.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface AbsRowIndex
{
    public static final @AbsRowIndex int ONE = 1;
}
