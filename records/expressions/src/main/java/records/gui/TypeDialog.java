package records.gui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.jellytype.JellyType;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Optional;

/**
 * Type dialog.
 */
@OnThread(Tag.FXPlatform)
public class TypeDialog extends Dialog<Optional<JellyType>>
{
    private final TypeSelectionPane typeSelectionPane;

    public TypeDialog(@Nullable Window parent, TypeManager typeManager, boolean emptyAllowed)
    {
        initModality(Modality.APPLICATION_MODAL);
        if (parent != null)
            initOwner(parent);
        typeSelectionPane = new TypeSelectionPane(typeManager, emptyAllowed);

        getDialogPane().setContent(typeSelectionPane.getNode());
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");

        setResultConverter(bt -> {
            if (bt == ButtonType.OK)
                return typeSelectionPane.selectedType().get();
            else
                return null;
        });
    }
}
