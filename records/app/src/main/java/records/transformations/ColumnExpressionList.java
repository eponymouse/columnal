package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
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
import records.gui.expressioneditor.ErrorDisplayerRecord;
import records.gui.expressioneditor.ExpressionEditor;
import records.transformations.expression.Expression;
import records.transformations.expression.UnfinishedExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.ScrollPaneFill;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A GUI list of column names and expression editors for that column.
 */
@OnThread(Tag.FXPlatform)
public class ColumnExpressionList
{
    private final List<Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>>> columns = new ArrayList<>();
    private final ScrollPaneFill columnListScrollPane;
    private SimpleBooleanProperty allColNamesValid = new SimpleBooleanProperty(false);
    private final BorderPane outerPane;
    private final VBox columnEditors;

    public ColumnExpressionList(TableManager mgr, SingleSourceControl srcTableControl, List<Pair<ColumnId, Expression>> initialColumns)
    {
        ObjectExpression<@Nullable Table> srcTable = srcTableControl.tableProperty();

        columnEditors = new VBox();
        columnEditors.getChildren().add(GUI.button("columnExpression.addEnd", () -> {
            // Important that columns.size() is a fresh call, so that we add to current end of list,
            // not the original end of list:
            addColumn(mgr, srcTable, makeNewColumnDetails(), columns.size());
        }));
        for (Pair<ColumnId, Expression> newColumn : initialColumns)
        {
            addColumn(mgr, srcTable, newColumn, columns.size());
        }
        columnListScrollPane = new ScrollPaneFill();
        columnListScrollPane.setContent(columnEditors);
        validateColumnNames();

        outerPane = new BorderPane();
        BorderPane top = new BorderPane();
        top.setLeft(GUI.label("transformEditor.column.name"));
        top.setCenter(GUI.label("transformEditor.column.type"));
        top.getStyleClass().add("column-expression-list-titles");
        outerPane.setTop(top);
        outerPane.setCenter(columnListScrollPane);
        outerPane.getStyleClass().add("column-expression-list");
    }

    @RequiresNonNull({"allColNamesValid", "columns", "columnEditors"})
    private void addColumn(@UnknownInitialization(Object.class) ColumnExpressionList this,
                           TableManager mgr, ObjectExpression<@Nullable Table> srcTable, Pair<ColumnId, Expression> newColumn, int destIndex)
    {
        SimpleObjectProperty<Expression> wrapper = new SimpleObjectProperty<>(newColumn.getSecond());
        ColumnNameTextField columnNameTextField = new ColumnNameTextField(newColumn.getFirst()).withArrowLocation(ArrowLocation.BOTTOM_CENTER);
        FXUtility.addChangeListenerPlatform(columnNameTextField.valueProperty(), v -> {
            validateColumnNames();
        });
        Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>> columnPair = new Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>>(columnNameTextField.valueProperty(), wrapper);
        this.columns.add(destIndex, columnPair);
        GridPane gridPane = new GridPane();
        gridPane.add(columnNameTextField.getNode(), 0, 0);
        ExpressionEditor expressionEditor = makeExpressionEditor(mgr, srcTable, wrapper);
        TypeLabel typeLabel = new TypeLabel(expressionEditor.typeProperty());
        gridPane.add(typeLabel, 1, 0);
        GridPane.setHgrow(typeLabel, Priority.ALWAYS);
        gridPane.add(GUI.buttonMenu("columnExpression.actions", () -> {
            return new ContextMenu(
                GUI.menuItem("columnExpression.delete", () -> {
                    columns.remove(columnPair);
                    columnEditors.getChildren().remove(gridPane);
                }),
                GUI.menuItem("columnExpression.copy", () -> {
                    // Don't use destIndex, because it might be stale.  Instead find us in list:
                    int curIndex = Utility.indexOfRef(columns, columnPair);
                    addColumn(mgr, srcTable, new Pair<>(Optional.ofNullable(columnNameTextField.valueProperty().getValue()).orElse(new ColumnId("")), expressionEditor.save(new ErrorDisplayerRecord(), err -> {})), curIndex + 1);
                }),
                GUI.menuItem("columnExpression.addBefore", () -> {
                    // Don't use destIndex, because it might be stale.  Instead find us in list:
                    int curIndex = Utility.indexOfRef(columns, columnPair);
                    addColumn(mgr, srcTable, makeNewColumnDetails(), curIndex);
                }),
                GUI.menuItem("columnExpression.addAfter", () -> {
                    // Don't use destIndex, because it might be stale.  Instead find us in list:
                    int curIndex = Utility.indexOfRef(columns, columnPair);
                    addColumn(mgr, srcTable, makeNewColumnDetails(), curIndex + 1);
                })
            );
        }), 2, 0);

        BorderPane containerAndLabel = new BorderPane(expressionEditor.getContainer(), null, null, null, new Label("="));
        gridPane.add(containerAndLabel, 0, 1);
        GridPane.setColumnSpan(containerAndLabel, 3);
        GridPane.setHgrow(containerAndLabel, Priority.ALWAYS);
        columnEditors.getChildren().add(destIndex, gridPane);
    }

    private static Pair<ColumnId, Expression> makeNewColumnDetails()
    {
        return new Pair<>(new ColumnId(""), new UnfinishedExpression(""));
    }


    @RequiresNonNull({"allColNamesValid", "columns"})
    private void validateColumnNames(@UnknownInitialization ColumnExpressionList this)
    {
        allColNamesValid.set(columns.stream().allMatch((Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>> p) -> p.getFirst().get() != null));
        //TODO: also check for duplicates within this list
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
