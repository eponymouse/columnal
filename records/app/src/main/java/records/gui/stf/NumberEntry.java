package records.gui.stf;

import records.error.UserException;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.Utility;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class NumberEntry implements Component<Number>
{
    private final Number initial;

    public NumberEntry(Number initial)
    {
        this.initial = initial;
    }

    @Override
    public List<Item> getInitialItems()
    {
        return Collections.singletonList(new Item(initial instanceof BigDecimal ? ((BigDecimal)initial).toPlainString() : initial.toString(), ItemVariant.EDITABLE_NUMBER, ""));
    }

    @Override
    public Either<List<ErrorFix>, Number> endEdit(StructuredTextField<?> field)
    {
        try
        {
            return Either.right(Utility.parseNumber(field.getItem(ItemVariant.EDITABLE_NUMBER)));
        }
        catch (UserException e)
        {
            return Either.left(Collections.emptyList());
        }
    }
}
