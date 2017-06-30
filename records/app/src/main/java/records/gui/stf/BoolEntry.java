package records.gui.stf;

import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class BoolEntry implements Component<Boolean>
{
    private final boolean initial;

    public BoolEntry(boolean initial)
    {
        this.initial = initial;
    }

    @Override
    public List<Item> getItems()
    {
        return Collections.singletonList(new Item(Boolean.toString(initial), ItemVariant.EDITABLE_BOOLEAN, ""));
    }

    @Override
    public Either<List<ErrorFix>, Boolean> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        String val = getItem(endResult, ItemVariant.EDITABLE_BOOLEAN).trim().toLowerCase();
        if (val.equals("true"))
            return Either.right(true);
        else if (val.equals("false"))
            return Either.right(false);
        else
            return Either.left(Collections.emptyList());
    }
}
