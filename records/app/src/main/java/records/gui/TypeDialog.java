package records.gui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Type dialog.
 */
@OnThread(Tag.FXPlatform)
public class TypeDialog extends Dialog<DataType>
{
    private final TypeSelectionPane typeSelectionPane;

    public TypeDialog(@Nullable Window parent, TypeManager typeManager)
    {
        initModality(Modality.APPLICATION_MODAL);
        if (parent != null)
            initOwner(parent);
        typeSelectionPane = new TypeSelectionPane(typeManager);

        getDialogPane().setContent(typeSelectionPane.getNode());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return typeSelectionPane.selectedType().get();
            else
                return null;
        });
    }
}
