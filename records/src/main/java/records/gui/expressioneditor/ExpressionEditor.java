package records.gui.expressioneditor;

import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Table;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.InvalidExpression;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
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
    private final FXPlatformConsumer<@NonNull Expression> onChange;
    private final TypeManager typeManager;

    public ExpressionEditor(@Nullable Expression startingValue, @Nullable Table srcTable, @Nullable DataType type, TypeManager typeManager, FXPlatformConsumer<@NonNull Expression> onChangeHandler)
    {
        super(null, null, null);
        this.container = new FlowPane();
        this.typeManager = typeManager;
        container.getStyleClass().add("expression-editor");
        Utility.ensureFontLoaded("NotoSans-Regular.ttf");
        container.getStylesheets().add(Utility.getStylesheet("expression-editor.css"));
        this.srcTable = srcTable;
        this.type = type;
        container.getChildren().setAll(nodes());
        Utility.listen(nodes(), c -> {
            container.getChildren().setAll(nodes());
        });
        this.onChange = onChangeHandler;

        //Utility.onNonNull(container.sceneProperty(), s -> org.scenicview.ScenicView.show(s));
    }

    public Node getContainer()
    {
        return container;
    }

//    @Override
//    public @Nullable DataType getType(ExpressionNode child)
//    {
//        return type;
//    }

    @Override
    public List<Column> getAvailableColumns()
    {
        if (srcTable == null)
            return Collections.emptyList();
        try
        {
            return srcTable.getData().getColumns();
        }
        catch (UserException e)
        {
            Utility.log(e);
            return Collections.emptyList();
        }
        catch (InternalException e)
        {
            Utility.report(e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(ExpressionNode child)
    {
        // No variables from outside the expression:
        return Collections.emptyList();
    }

    @Override
    public boolean isTopLevel(@UnknownInitialization(Consecutive.class) ExpressionEditor this)
    {
        return true;
    }

    @Override
    protected void selfChanged(@UnknownInitialization(Consecutive.class) ExpressionEditor this)
    {
        // Can be null during initialisation
        if (onChange != null)
        {
            @Nullable Expression expression = toExpression(err -> {});
            if (expression != null)
                onChange.consume(expression);
            else
                onChange.consume(new InvalidExpression());
        }
    }

    @Override
    public TypeManager getTypeManager() throws InternalException
    {
        return typeManager;
    }
}
