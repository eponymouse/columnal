package records.gui;

import com.google.common.collect.ImmutableSet;
import javafx.beans.binding.BooleanExpression;
import javafx.geometry.Insets;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.Table;
import records.gui.AutoComplete.CompletionListener;
import records.gui.AutoComplete.SimpleCompletion;
import records.gui.AutoComplete.WhitespacePolicy;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.gui.FXUtility;
import utility.gui.Instruction;

import java.util.List;

@OnThread(Tag.FXPlatform)
public class PickTablePane extends BorderPane
{
    private final TextField tableField = new TextField();
    private final AutoComplete autoComplete;
    private final FXPlatformConsumer<Table> setResultAndClose;
    private long lastEditTimeMillis = -1;
    private final Instruction instruction;

    public PickTablePane(View view, ImmutableSet<Table> exclude, String initial, FXPlatformConsumer<Table> setResultAndFinishEditing)
    {
        this.setResultAndClose = setResultAndFinishEditing;
        tableField.setText(initial);
        autoComplete = new AutoComplete<TableCompletion>(tableField,
            s -> view.getManager().getAllTables().stream().filter(t -> !exclude.contains(t) && t.getId().getOutput().contains(s)).map(TableCompletion::new),
            getListener(view.getManager().getAllTables()), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
        
        setCenter(tableField);
        instruction = new Instruction("pick.table.instruction");
        instruction.showAboveWhenFocused(tableField);
        setMargin(tableField, new Insets(0, 4, 4, 4));
        
        FXUtility.addChangeListenerPlatformNN(tableField.focusedProperty(), focus -> {
            // Update whether focus is arriving or leaving:
            lastEditTimeMillis = System.currentTimeMillis();
        });
        getStyleClass().add("pick-table-pane");
    }

    public void focusEntryField()
    {
        tableField.requestFocus();
    }

    @RequiresNonNull("setResultAndClose")
    private CompletionListener<TableCompletion> getListener(@UnknownInitialization(BorderPane.class) PickTablePane this, List<Table> tables)
    {
        @NonNull FXPlatformConsumer<Table> setResultAndCloseFinal = setResultAndClose;
        return new CompletionListener<TableCompletion>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public String doubleClick(String currentText, TableCompletion selectedItem)
            {
                return complete(selectedItem.t);
            }

            @OnThread(Tag.FXPlatform)
            protected String complete(Table t)
            {
                setResultAndCloseFinal.consume(t);
                return t.getId().getOutput();
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public @Nullable String keyboardSelect(String textBefore, String textAfter, @Nullable TableCompletion selectedItem, boolean wasTab)
            {
                if (selectedItem != null)
                    return complete(selectedItem.t);
                else
                {
                    Table t = tables.stream().filter(table -> table.getId().getRaw().equals(textBefore + textAfter)).findFirst().orElse(null);
                    if (t != null)
                        return complete(t);
                }
                return null;
            }
        };
    }

    public void setContent(@Nullable Table table)
    {
        autoComplete.setContentDirect(table == null ? "" : table.getId().getRaw(), true);
        if (table != null)
            setResultAndClose.consume(table);
    }

    public BooleanExpression currentlyEditing()
    {
        return tableField.focusedProperty();
    }
    
    public long lastEditTimeMillis()
    {
        return tableField.isFocused() ? System.currentTimeMillis() : lastEditTimeMillis;
    }

    private static class TableCompletion extends SimpleCompletion
    {
        private final Table t;

        public TableCompletion(Table t)
        {
            super(t.getId().getRaw(), null);
            this.t = t;
        }
    }
}
