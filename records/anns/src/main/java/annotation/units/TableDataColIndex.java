package annotation.units;

import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A column index (X position) within a table's data display.  0 is the first data column.
 * Only valid for data, not for expand arrows, headers, etc.
 */
@Documented
@SubtypeOf(RowOrColIndex.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface TableDataColIndex
{
    public static final @TableDataColIndex int ONE = 1;
}
