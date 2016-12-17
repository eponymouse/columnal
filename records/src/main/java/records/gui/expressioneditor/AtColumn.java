package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import records.data.ColumnId;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 17/12/2016.
 */
public class AtColumn extends ExpressionNode
{
    private final TextField textField;
    private final AutoComplete autoComplete;
    private final ObservableList<Node> nodes;

    @SuppressWarnings("initialization")
    public AtColumn(String content, ExpressionParent parent)
    {
        super(parent);
        this.textField = new TextField(content) {
            @Override
            @OnThread(value = Tag.FXPlatform,ignoreParent = true)
            public boolean deletePreviousChar()
            {
                if (getCaretPosition() == 0 && getAnchor() == 0)
                {
                    parent.replace(AtColumn.this, new GeneralEntry(getText(), parent).focusWhenShown());
                    return false;
                }
                else
                    return super.deletePreviousChar();
            }
        };
        this.nodes = FXCollections.observableArrayList(new Label("@"), textField);
        this.autoComplete = new AutoComplete(textField, this::calculateSuggestions);
    }

    private List<Completion> calculateSuggestions(String text)
    {
        return parent.getAvailableColumns().stream().map(col -> new SimpleCompletion(col.getRaw())).filter(c -> c.shouldShow(text)).collect(Collectors.<Completion>toList());
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    public ExpressionNode focusWhenShown()
    {
        Utility.onNonNull(textField.sceneProperty(), scene -> {textField.requestFocus();});
        return this;
    }
}
