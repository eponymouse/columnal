package annotation.identifier.qual;

import annotation.help.qual.UnknownIfHelp;
import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * A String which is valid Expression identifier.
 */
@SubtypeOf(UnitIdentifier.class)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface ExpressionIdentifier
{
}
