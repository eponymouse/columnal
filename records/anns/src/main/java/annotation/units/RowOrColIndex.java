package annotation.units;

import org.checkerframework.checker.units.qual.UnknownUnits;
import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE_PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@SubtypeOf(value=UnknownUnits.class)
@Documented
@Retention(value=RUNTIME)
@Target(value={TYPE_USE,TYPE_PARAMETER})
public @interface RowOrColIndex
{
}
