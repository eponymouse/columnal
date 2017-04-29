package records.gui;

import annotation.qual.Value;

/**
 * A DisplayValue which comes from the user entering a string.
 *
 * Because of the way TableView works, we have to send the string
 * through a DisplayValueBase type before it can be stored, even
 * though really DisplayValue was intended only for display, not for entry.
 */
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
