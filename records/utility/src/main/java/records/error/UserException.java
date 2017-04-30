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

    @SuppressWarnings({"nullness", "i18n"}) // Given our constructors require non-null, this can't return null.
    // Also, putting @Localized on Exception.getLocalizedMessage doesn't seem to prevent error here, so suppress i18n warning.
    @Override
    public @NonNull @Localized String getLocalizedMessage()
    {
        return super.getLocalizedMessage();
    }
}
