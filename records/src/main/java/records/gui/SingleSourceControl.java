package records.gui;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 12/02/2017.
 */
@OnThread(Tag.FXPlatform)
public class SingleSourceControl extends HBox
{
    private final @Nullable TableId curSelection;
    private final TableManager mgr;

    @SuppressWarnings("initialization")
    public SingleSourceControl(View view, TableManager mgr, @Nullable TableId srcTableId)
    {
        this.mgr = mgr;
        this.curSelection = srcTableId;
        Label label = new Label("Source:");
        TextField selected = new TextField(srcTableId == null ? "" : srcTableId.getOutput());
        // TODO add an auto complete
        Button select = new Button("Choose...");
        select.setOnAction(e -> {
            Window window = getScene().getWindow();
            window.hide(); // Or fold up?
            view.pickTable(picked -> {
                selected.setText(picked.getId().getOutput());
                ((Stage)window).show();
            });
        });
        // TODO implement it

        super.getChildren().addAll(label, selected, select);
    }

    public @Nullable TableId getTableIdOrNull()
    {
        return curSelection;
    }

    @Pure
    public @Nullable Table getTableOrNull()
    {
        return curSelection == null ? null : mgr.getSingleTableOrNull(curSelection);
    }

    @OnThread(Tag.Any)
    public TableId getTableIdOrThrow() throws InternalException
    {
        if (curSelection == null)
        {
            throw new InternalException("Trying to create transformation even though source table is unspecified");
        }
        return curSelection;
    }
}
