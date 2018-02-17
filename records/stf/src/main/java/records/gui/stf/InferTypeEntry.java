package records.gui.stf;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataTypeUtility;
import records.grammar.GrammarUtility;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class InferTypeEntry extends TerminalComponent<@Value String>
{
    public InferTypeEntry(ImmutableList<Component<?>> parents, String initial)
    {
        super(parents);
        items.addAll(Arrays.asList(new Item(getItemParents(), GrammarUtility.escapeChars(initial), ItemVariant.EDITABLE_TEXT, "")));
    }

    @Override
    public Either<List<ErrorFix>, @Value String> endEdit(StructuredTextField field)
    {
        return Either.right(DataTypeUtility.value(getItem(ItemVariant.EDITABLE_TEXT)));
    }
}
