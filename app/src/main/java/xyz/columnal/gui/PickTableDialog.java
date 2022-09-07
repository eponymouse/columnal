package records.gui;

import com.google.common.collect.ImmutableSet;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.gui.DialogPaneWithSideButtons;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.LightDialog;

@OnThread(Tag.FXPlatform)
public class PickTableDialog extends LightDialog<Table>
{
    public PickTableDialog(View view, @Nullable Table destTable, Point2D lastScreenPos)
    {
        // We want the cancel button to appear to the right, because otherwise the auto complete hides it:
        super(view, new DialogPaneWithSideButtons());
        initModality(Modality.NONE);


        // Should also exclude tables which use destination as a source, to prevent cycles:
        ImmutableSet<Table> excludeTables = destTable == null ? ImmutableSet.of() : ImmutableSet.of(destTable);
        PickTablePane pickTablePane = new PickTablePane(view, excludeTables, "", t -> {
            setResult(t);
            close();
        });
        pickTablePane.setFieldPrefWidth(400.0);
        getDialogPane().setContent(pickTablePane);

        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        FXUtility.fixButtonsWhenPopupShowing(getDialogPane());
        
        setOnShowing(e -> {
            view.enableTablePickingMode(lastScreenPos, getDialogPane().sceneProperty(), excludeTables, t -> {
                // We shouldn't need the mouse call here, I think this is a checker framework bug:
                FXUtility.mouse(this).setResult(t);
                close();
            });
            pickTablePane.focusEntryField();
        });
        setOnHiding(e -> {
            view.disablePickingMode();
        });
        getDialogPane().getStyleClass().add("pick-table-dialog");
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

}
