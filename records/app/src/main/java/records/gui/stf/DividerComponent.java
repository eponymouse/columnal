package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import utility.Either;

import java.util.List;

/**
 * Created by neil on 08/07/2017.
 */
public class DividerComponent extends TerminalComponent<@Nullable Void>
{
    public DividerComponent(ImmutableList<Component<?>> componentParents, String divider)
    {
        super(componentParents);
        items.add(new Item(getItemParents(), divider));
    }

    @Override
    public Either<List<ErrorFix>, @Nullable Void> endEdit(StructuredTextField<?> field)
    {
        return Either.right(null);
    }

    @Override
    public boolean hasOuterBrackets()
    {
        return items.get(0).getValue().equals("(");
    }
}
