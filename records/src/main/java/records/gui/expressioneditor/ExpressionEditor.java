package records.gui.expressioneditor;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import org.checkerframework.checker.interning.qual.UnknownInterned;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.UnknownKeyFor;
import records.data.ColumnId;
import records.data.Table;
import records.data.datatype.DataType;
import records.error.UserException;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
import utility.Utility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 17/12/2016.
 */
public class ExpressionEditor extends Consecutive
{
    private final FlowPane container;
    private final @Nullable DataType type;
    private final @Nullable Table srcTable;
    private final FXPlatformConsumer<@Nullable Expression> onChange;

    @SuppressWarnings("initialization")
    public ExpressionEditor(@Nullable Expression startingValue, @Nullable Table srcTable, @Nullable DataType type, FXPlatformConsumer<@Nullable Expression> onChange)
    {
        super(null, null, null);
        this.container = new FlowPane();
        container.getStyleClass().add("expression-editor");
        Utility.ensureFontLoaded("NotoSans-Regular.ttf");
        container.getStylesheets().add(Utility.getStylesheet("expression-editor.css"));
        this.srcTable = srcTable;
        this.type = type;
        container.getChildren().setAll(nodes());
        Utility.listen(nodes(), c -> {
            container.getChildren().setAll(nodes());
        });
        this.onChange = onChange;
        //Utility.onNonNull(container.sceneProperty(), s -> org.scenicview.ScenicView.show(s));
    }

    public Node getContainer()
    {
        return container;
    }

    @Override
    public @Nullable DataType getType(ExpressionNode child)
    {
        return type;
    }

    @Override
    public List<ColumnId> getAvailableColumns()
    {
        if (srcTable == null)
            return Collections.emptyList();
        try
        {
            return srcTable.getData().getColumnIds();
        }
        catch (UserException e)
        {
            Utility.log(e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> getAvailableVariables(ExpressionNode child)
    {
        // No variables from outside the expression:
        return Collections.emptyList();
    }

    @Override
    public boolean isTopLevel()
    {
        return true;
    }

    @Override
    protected void selfChanged()
    {
        // Can be null during initialisation
        if (onChange != null)
            onChange.consume(toExpression(err -> {}));
    }
}
