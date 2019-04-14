package annotation.units;

import org.checkerframework.checker.units.qual.UnknownUnits;
import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A location in the lex editor's display, including added spaces.
 */
@Documented
@SubtypeOf(value= UnknownUnits.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface DisplayLocation
{
    public static final @DisplayLocation int ZERO = 0;
    public static final @DisplayLocation int ONE = 1;
}
