package annotation.recorded.qual;

import org.checkerframework.framework.qual.ImplicitFor;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@SubtypeOf(UnknownIfRecorded.class)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface Recorded
{
}
