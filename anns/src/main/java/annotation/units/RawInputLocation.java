package annotation.units;

import org.checkerframework.checker.units.qual.UnknownUnits;
import org.checkerframework.framework.qual.SubtypeOf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A location in the raw input to a lex editor, before spaces are removed
 */
@Documented
@SubtypeOf(value= UnknownUnits.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface RawInputLocation
{
    public static @RawInputLocation int ONE = 1;
    public static @RawInputLocation int ZERO = 0;
}
