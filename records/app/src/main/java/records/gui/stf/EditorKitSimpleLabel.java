package records.gui.stf;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.StructuredTextField.EndEditActions;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.Collections;
import java.util.List;

// An uneditable EditorKit that displays a simple label (e.g. error message)
public class EditorKitSimpleLabel<T> extends EditorKit<T>
{
    public EditorKitSimpleLabel(@Localized String errorMessage)
    {
        super(new TerminalComponent<T>(ImmutableList.of())
        {
            {
                items.add(new Item(getItemParents(), errorMessage, ItemVariant.DIVIDER, ""));
            }
            @Override
            public Either<List<ErrorFix>, T> endEdit(StructuredTextField field)
            {
                return Either.left(Collections.emptyList());
            }
        }, p -> {});
    }
}
