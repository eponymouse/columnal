package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import records.gui.stf.StructuredTextField.Component;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import records.gui.stf.StructuredTextField.Suggestion;
import utility.Either;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class BoolEntry extends Component<Boolean>
{
    private final String initialContent;

    public BoolEntry(ImmutableList<Component<?>> parents, @Nullable Boolean initial)
    {
        super(parents);
        this.initialContent = initial == null ? "" : Boolean.toString(initial);
    }

    @Override
    public List<Item> getInitialItems()
    {
        return Collections.singletonList(new Item(getItemParents(), initialContent, ItemVariant.EDITABLE_BOOLEAN, ""));
    }

    @Override
    public Either<List<ErrorFix>, Boolean> endEdit(StructuredTextField<?> field, List<Item> endResult)
    {
        String val = getItem(endResult, ItemVariant.EDITABLE_BOOLEAN).trim().toLowerCase();
        if (val.equals("true"))
        {
            field.setItem(ItemVariant.EDITABLE_BOOLEAN, "true");
            field.lineEnd(SelectionPolicy.CLEAR);
            return Either.right(true);
        }
        else if (val.equals("false"))
        {
            field.setItem(ItemVariant.EDITABLE_BOOLEAN, "false");
            field.lineEnd(SelectionPolicy.CLEAR);
            return Either.right(false);
        }
        else
            return Either.left(Collections.emptyList());
    }

    @Override
    public List<Suggestion> getSuggestions()
    {
        return Arrays.asList(new Suggestion(0, 0, "true"), new Suggestion(0,0, "false"));
    }
}
