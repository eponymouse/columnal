package annotation.recorded.qual;

import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeUseLocation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@SubtypeOf(Recorded.class)
@DefaultFor(TypeUseLocation.LOWER_BOUND)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface RecordedBottom
{
}
