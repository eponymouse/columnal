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

    @SuppressWarnings("initialization")
    public ExpressionEditor(@Nullable Expression startingValue, @Nullable Table srcTable, @Nullable DataType type)
    {
        super(Collections.emptyList(), new ExpressionParent() {

            @Override
            public void replace(ExpressionNode oldNode, ExpressionNode newNode)
            {
            }

            @Override
            public void addToRight(ExpressionNode rightOf, ExpressionNode... newNode)
            {
            }

            @Override
            public @Nullable DataType getType(ExpressionNode child)
            {
                return type;
            }

            @Override
            public List<ColumnId> getAvailableColumns()
            {
                return Collections.emptyList();
            }

            @Override
            public void deleteOneLeftOf(ExpressionNode child)
            {

            }

            @Override
            public void deleteOneRightOf(ExpressionNode child)
            {

            }
        });
        addToRight(null, new GeneralEntry("", this));
        this.container = new FlowPane();
        container.getStyleClass().add("expression-editor");
        Utility.ensureFontLoaded("NotoSans-Regular.ttf");
        container.getStylesheets().add(Utility.getStylesheet("expression-editor.css"));
        this.srcTable = srcTable;
        this.type = type;
        container.getChildren().setAll(nodes());
        nodes().addListener((ListChangeListener<? super @UnknownInterned @UnknownKeyFor Node>) c -> {
            container.getChildren().setAll(nodes());
        });
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
}
