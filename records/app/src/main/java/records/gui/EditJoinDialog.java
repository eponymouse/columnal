package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.Table;
import records.data.TableId;
import records.transformations.Join;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.TranslationUtility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.GUI;
import utility.gui.LabelledGrid;
import utility.gui.LabelledGrid.Row;

/**
 * Dialog to edit join.  Allows you to edit the two source tables,
 * a tickbox whether to do left join or inner join, and pairs
 * of columns on which to join.
 */
@OnThread(Tag.FXPlatform)
public class EditJoinDialog extends ErrorableLightDialog<EditJoinDialog.JoinDetails>
{
    private final PickTablePane primaryTableNamePane;
    private final PickTablePane secondaryTableNamePane;
    private final CheckBox isLeftJoin;
    private final ColumnList joinOn;
    
    public EditJoinDialog(View parent, Join join)
    {
        super(parent, true);
        FXUtility.preventCloseOnEscape(getDialogPane());
        setResizable(true);
        Pair<CheckBox, Row> leftJoinRow = LabelledGrid.tickGridRow("join.isLeftJoin", "join/left-join", new Label());
        isLeftJoin = leftJoinRow.getFirst();
        isLeftJoin.setSelected(join.isKeepPrimaryWithNoMatch());
        joinOn = new ColumnList((ImmutableList<Pair<@Nullable ColumnId, @Nullable ColumnId>>)join.getColumnsToMatch());
        ImmutableSet<Table> exclude = ImmutableSet.of(join); 
        secondaryTableNamePane = new PickTablePane(parent, exclude, join.getSecondarySource().getRaw(), t -> {
            joinOn.focusAddButton();
        });
        primaryTableNamePane = new PickTablePane(parent, exclude, join.getPrimarySource().getRaw(), t -> {
            secondaryTableNamePane.focusEntryField();
        });
        // TODO add table and column picking
        //primaryTableNamePane.focusedProperty()parent.enableTablePickingMode();
        
        getDialogPane().setPrefWidth(600.0);
        getDialogPane().setContent(GUI.borderTopCenterBottom(
                GUI.borderLeftRight(primaryTableNamePane, secondaryTableNamePane),
                joinOn.getNode(),
                new LabelledGrid(leftJoinRow.getSecond(), LabelledGrid.fullWidthRow(getErrorLabel()))
        ));
        FXUtility.onceNotNull(primaryTableNamePane.sceneProperty(), s -> FXUtility.runAfter(() -> primaryTableNamePane.focusEntryField()));
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, JoinDetails> calculateResult()
    {
        TableId prim = this.primaryTableNamePane.getValue();
        TableId sec = this.secondaryTableNamePane.getValue();
        if (prim == null || sec == null)
            return Either.left(TranslationUtility.getString("join.invalid.table.names"));
        ImmutableList.Builder<Pair<ColumnId, ColumnId>> joinColumns = ImmutableList.builder();
        for (Pair<@Nullable ColumnId, @Nullable ColumnId> item : joinOn.getItems())
        {
            if (item.getFirst() == null || item.getSecond() == null)
                return Either.left(TranslationUtility.getString("join.invalid.column.names"));
            joinColumns.add(new Pair<>(item.getFirst(), item.getSecond()));
        }
        return Either.right(new JoinDetails(prim, sec, isLeftJoin.isSelected(), joinColumns.build()));
    }

    private class ColumnList extends FancyList<Pair<@Nullable ColumnId, @Nullable ColumnId>, DualColumnPane>
    {

        public ColumnList(ImmutableList<Pair<@Nullable ColumnId, @Nullable ColumnId>> initialItems)
        {
            super(initialItems, true, false, true);
        }

        @Override
        protected @OnThread(Tag.FXPlatform) Pair<DualColumnPane, FXPlatformSupplier<Pair<@Nullable ColumnId, @Nullable ColumnId>>> makeCellContent(@Nullable Pair<@Nullable ColumnId, @Nullable ColumnId> initialContent, boolean editImmediately)
        {
            DualColumnPane dualColumnPane = new DualColumnPane(initialContent);
            if (editImmediately)
                FXUtility.onceNotNull(dualColumnPane.sceneProperty(), s -> FXUtility.runAfter(dualColumnPane.primaryColumn::requestFocus));
            return new Pair<DualColumnPane, FXPlatformSupplier<Pair<@Nullable ColumnId, @Nullable ColumnId>>>(dualColumnPane, dualColumnPane::getValue);
        }
    }
    
    private class DualColumnPane extends BorderPane
    {
        private final TextField primaryColumn;
        private final TextField secondaryColumn;

        public DualColumnPane(@Nullable Pair<@Nullable ColumnId, @Nullable ColumnId> initialContent)
        {
            this.primaryColumn = new TextField();
            this.secondaryColumn = new TextField();
            if (initialContent != null && initialContent.getFirst() != null)
                primaryColumn.setText(initialContent.getFirst().getRaw());
            if (initialContent != null && initialContent.getSecond() != null)
                secondaryColumn.setText(initialContent.getSecond().getRaw());
            setLeft(primaryColumn);
            setRight(secondaryColumn);
        }

        public Pair<@Nullable ColumnId, @Nullable ColumnId> getValue()
        {
            @ExpressionIdentifier String primaryIdent = IdentifierUtility.asExpressionIdentifier(primaryColumn.getText());
            @ExpressionIdentifier String secondaryIdent = IdentifierUtility.asExpressionIdentifier(secondaryColumn.getText());
            return new Pair<>(primaryIdent == null ? null : new ColumnId(primaryIdent), secondaryIdent == null ? null : new ColumnId(secondaryIdent));
        }
    }
    
    public static class JoinDetails
    {
        public final TableId primaryTableId;
        public final TableId secondaryTableId;
        public final boolean isLeftJoin;
        public final ImmutableList<Pair<ColumnId, ColumnId>> joinOn;

        public JoinDetails(TableId primaryTableId, TableId secondaryTableId, boolean isLeftJoin, ImmutableList<Pair<ColumnId, ColumnId>> joinOn)
        {
            this.primaryTableId = primaryTableId;
            this.secondaryTableId = secondaryTableId;
            this.isLeftJoin = isLeftJoin;
            this.joinOn = joinOn;
        }
    }
}
