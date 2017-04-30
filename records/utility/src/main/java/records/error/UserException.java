package records.error;

import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 22/10/2016.
 */
@OnThread(Tag.Any)
public class UserException extends Exception
{
    public UserException(String message)
    {
        super(message);
    }

    public UserException(String message, Throwable cause)
    {
        super(message, cause);
    }

    @SuppressWarnings("nullness") // Given our constructors require non-null, this can't return null:
    @Override
    public @NonNull @Localized String getLocalizedMessage()
    {
        return super.getLocalizedMessage();
    }
}
