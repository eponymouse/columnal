package annotation.units;

import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A column index (X position) within a given grid area.  Zero is the leftmost column in that
 * grid-area's declared area.  It is possible for this index to be negative or otherwise out of bounds
 * if there is an overlay that extends outside (e.g. row labels which are outside to the left).
 */
@Documented
@SubtypeOf(RowOrColIndex.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface GridAreaColIndex
{
}
