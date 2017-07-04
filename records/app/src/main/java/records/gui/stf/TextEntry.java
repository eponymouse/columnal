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
public class TextEntry implements Component<String>
{
    private final String initial;

    public TextEntry(String initial)
    {
        this.initial = initial;
    }

    @Override
    public List<Item> getItems()
    {
        return Collections.singletonList(new Item(this, initial, ItemVariant.EDITABLE_TEXT, ""));
    }

    @Override
    public Either<List<ErrorFix>, String> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        return Either.right(getItem(endResult, ItemVariant.EDITABLE_TEXT));
    }
}
