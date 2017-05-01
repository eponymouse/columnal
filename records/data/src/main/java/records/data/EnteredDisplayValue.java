package records.data;

import annotation.qual.Value;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A DisplayValue which comes from the user entering a string.
 *
 * Because of the way TableView works, we have to send the string
 * through a DisplayValueBase type before it can be stored, even
 * though really DisplayValue was intended only for display, not for entry.
 */
@OnThread(Tag.FX)
public class EnteredDisplayValue extends DisplayValueBase
{
    private final String enteredString;

    public EnteredDisplayValue(int rowIndex, String enteredString)
    {
        super(rowIndex);
        this.enteredString = enteredString;
    }

    @Override
    public String toString()
    {
        return enteredString;
    }

    @SuppressWarnings("value") // To add @Value annotation
    @OnThread(Tag.Any)
    public @Value String getString()
    {
        return enteredString;
    }

    @Override
    public String getEditString()
    {
        // Not sure this method should get called on this class, but just in case:
        return enteredString;
    }
}
