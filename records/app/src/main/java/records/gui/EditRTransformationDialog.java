package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.checkerframework.checker.i18n.qual.Localized;
import records.data.Table;
import records.data.TableId;
import records.gui.EditRTransformationDialog.RDetails;
import records.transformations.RTransformation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.Utility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.LabelledGrid;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class EditRTransformationDialog extends ErrorableLightDialog<RDetails>
{
    private final View parent;
    private final RTransformation existing;
    private final TableList tableList;
    private final TextArea expressionTextArea;
    private final TextField packageField;

    public static class RDetails
    {
        public final ImmutableList<TableId> includedTables;
        public final ImmutableList<String> packages;
        public final String rExpression;

        public RDetails(ImmutableList<TableId> includedTables, ImmutableList<String> packages, String rExpression)
        {
            this.includedTables = includedTables;
            this.packages = packages;
            this.rExpression = rExpression;
        }
    }
    
    public EditRTransformationDialog(View parent, RTransformation existing, boolean selectWholeExpression)
    {
        super(parent, true);
        this.parent = parent;
        this.existing = existing;
        setResizable(true);
        tableList = new TableList(existing.getInputTables());
        packageField = new TextField(existing.getPackagesToLoad().stream().collect(Collectors.joining(", ")));
        expressionTextArea = new TextArea(existing.getRExpression());
        // For some reason, this seems to produce a width similar to 70 chars:
        expressionTextArea.setPrefColumnCount(30);
        expressionTextArea.setPrefRowCount(6);
        getDialogPane().setContent(new LabelledGrid(
                LabelledGrid.labelledGridRow("edit.r.srcTables", "edit-r-srctables", tableList.getNode()),
                LabelledGrid.labelledGridRow("edit.r.packages", "edit-r-packages", packageField),
                LabelledGrid.labelledGridRow("edit.r.expression", "edit-r-expression", expressionTextArea),
                LabelledGrid.fullWidthRow(getErrorLabel())
        ));
        Platform.runLater(() -> {
            expressionTextArea.requestFocus();
            if (selectWholeExpression)
                expressionTextArea.selectAll();
            else
                expressionTextArea.end();
        });
    }

    @Override
    protected Either<@Localized String, RDetails> calculateResult()
    {
        ImmutableList.Builder<TableId> tables = ImmutableList.builder();
        for (String item : tableList.getItems())
        {
            @ExpressionIdentifier String t = IdentifierUtility.asExpressionIdentifier(item);
            if (t == null)
                return Either.left("Invalid table identifier: \"" + t + "\"");
            tables.add(new TableId(t));
        }
        String rExpression = expressionTextArea.getText().trim();
        if (rExpression.isEmpty())
            return Either.left("R expression cannot be blank");
        return Either.right(new RDetails(tables.build(), Arrays.stream(packageField.getText().split(",")).map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(ImmutableList.<String>toImmutableList()), rExpression));
    }


    @OnThread(Tag.FXPlatform)
    private class TableList extends FancyList<String, PickTablePane>
    {
        public TableList(ImmutableList<TableId> originalItems)
        {
            super(Utility.mapListI(originalItems, t -> t.getRaw()), true, true, true);
            getStyleClass().add("table-list");
        }

        @Override
        protected Pair<PickTablePane, FXPlatformSupplier<String>> makeCellContent(Optional<String> original, boolean editImmediately)
        {
            if (!original.isPresent())
                original = Optional.of("");
            SimpleObjectProperty<String> curValue = new SimpleObjectProperty<>(original.get());
            PickTablePane pickTablePane = new PickTablePane(parent, ImmutableSet.of(existing), original.get(), t -> {
                curValue.set(t.getId().getRaw());
                focusAddButton();
            });
            FXUtility.addChangeListenerPlatformNN(pickTablePane.currentlyEditing(), ed -> {
                if (ed)
                {
                    clearSelection();
                }
            });
            if (editImmediately)
            {
                FXUtility.runAfter(() -> pickTablePane.focusEntryField());
            }
            return new Pair<>(pickTablePane, curValue::get);
        }

        public void pickTableIfEditing(Table t)
        {
            // This is a bit of a hack.  The problem is that clicking the table removes the focus
            // from the edit field, so we can't just ask if the edit field is focused.  Tracking who
            // the focus transfers from/to seems a bit awkward, so we just use a time-based system,
            // where if they were very recently (200ms) editing a field, fill in that field with the table.
            // If they weren't recently editing a field, we append to the end of the list.
            long curTime = System.currentTimeMillis();
            PickTablePane curEditing = streamCells()
                    .map(cell -> cell.getContent())
                    .filter(p -> p.lastEditTimeMillis() > curTime - 200L).findFirst().orElse(null);
            if (curEditing != null)
            {
                curEditing.setContent(t);
                focusAddButton();
            }
            else
            {
                // Add to end:
                addToEnd(t.getId().getRaw(), false);
            }
        }
    }

}
