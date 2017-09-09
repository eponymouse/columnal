package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver.ArrowLocation;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.gui.ColumnNameTextField;
import records.gui.SingleSourceControl;
import records.gui.TypeLabel;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.ScrollPaneFill;

import java.util.ArrayList;
import java.util.List;

@OnThread(Tag.FXPlatform)
public class ColumnExpressionList
{
    private final List<Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>>> columns = new ArrayList<>();
    private final ScrollPaneFill columnListScrollPane;
    private SimpleBooleanProperty allColNamesValid = new SimpleBooleanProperty(false);
    private final BorderPane outerPane;

    public ColumnExpressionList(TableManager mgr, SingleSourceControl srcTableControl, List<Pair<ColumnId, Expression>> initialColumns)
    {
        ObjectExpression<@Nullable Table> srcTable = srcTableControl.tableProperty();

        List<Node> columnEditors = new ArrayList<>();
        for (Pair<ColumnId, Expression> newColumn : initialColumns)
        {
            SimpleObjectProperty<Expression> wrapper = new SimpleObjectProperty<>(newColumn.getSecond());
            ColumnNameTextField columnNameTextField = new ColumnNameTextField(newColumn.getFirst()).withArrowLocation(ArrowLocation.BOTTOM_CENTER);
            FXUtility.addChangeListenerPlatform(columnNameTextField.valueProperty(), v -> {
                validateColumnNames();
            });
            this.columns.add(new Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>>(columnNameTextField.valueProperty(), wrapper));
            GridPane gridPane = new GridPane();
            gridPane.add(columnNameTextField.getNode(), 0, 0);
            ExpressionEditor expressionEditor = makeExpressionEditor(mgr, srcTable, wrapper);
            TypeLabel typeLabel = new TypeLabel(expressionEditor.typeProperty());
            gridPane.add(typeLabel, 1, 0);
            GridPane.setHgrow(typeLabel, Priority.ALWAYS);
            BorderPane containerAndLabel = new BorderPane(expressionEditor.getContainer(), null, null, null, new Label("="));
            gridPane.add(containerAndLabel, 0, 1);
            GridPane.setColumnSpan(containerAndLabel, 2);
            GridPane.setHgrow(containerAndLabel, Priority.ALWAYS);
            columnEditors.add(gridPane);
        }
        columnListScrollPane = new ScrollPaneFill();
        columnListScrollPane.setContent(new VBox(columnEditors.toArray(new Node[0])));
        validateColumnNames();

        outerPane = new BorderPane();
        BorderPane top = new BorderPane();
        top.setLeft(GUI.label("transformEditor.column.name"));
        top.setRight(GUI.label("transformEditor.column.type"));
        top.getStyleClass().add("column-expression-list-titles");
        outerPane.setTop(top);
        outerPane.setCenter(columnListScrollPane);
        outerPane.getStyleClass().add("column-expression-list");
    }


    @RequiresNonNull({"allColNamesValid", "columns"})
    private void validateColumnNames(@UnknownInitialization ColumnExpressionList this)
    {
        allColNamesValid.set(columns.stream().allMatch((Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>> p) -> p.getFirst().get() != null));
    }


    private static ExpressionEditor makeExpressionEditor(TableManager mgr, ObjectExpression<@Nullable Table> srcTable, SimpleObjectProperty<Expression> container)
    {
        return new ExpressionEditor(container.getValue(), srcTable, new ReadOnlyObjectWrapper<@Nullable DataType>(null), mgr, e -> {
            container.set(e);
        });
    }

    public BooleanExpression allColumnNamesValidProperty()
    {
        return allColNamesValid;
    }

    public Pane getNode()
    {
        return outerPane;
    }

    public List<Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>>> getColumns()
    {
        return columns;
    }
}
