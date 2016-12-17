package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.FunctionCompletion;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Anything which fits in a normal text field without special prefix:
 *   - Numeric literal
 *   - Boolean literal
 *   - Function name (until later transformed to function call)
 *   - Variable reference.
 */
public class GeneralEntry extends ExpressionNode
{
    private final TextField textField;
    private final ObservableList<Node> nodes;
    private final AutoComplete autoComplete;

    @SuppressWarnings("initialization")
    public GeneralEntry(String content, ExpressionParent parent)
    {
        super(parent);
        this.textField = new TextField(content) {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void replaceText(int start, int end, String text)
            {
                super.replaceText(start, end, text);
                int operator = getText().indexOf("/");
                if (operator != -1)
                {
                    Pair<String, String> split = splitAt(operator);
                    parent.addToRight(GeneralEntry.this, new Operator("/", parent), new GeneralEntry(split.getSecond(), parent).focusWhenShown());
                    setText(split.getFirst());
                }
                if (getText().startsWith("@"))
                {
                    // Turn into column reference
                    parent.replace(GeneralEntry.this, new AtColumn(getText().substring(1), parent).focusWhenShown());
                }
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public boolean deletePreviousChar()
            {
                if (getCaretPosition() == 0 && getAnchor() == 0)
                    parent.deleteOneLeftOf(GeneralEntry.this);
                return super.deletePreviousChar();
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public boolean deleteNextChar()
            {
                if (getCaretPosition() == getText().length() && getAnchor() == getText().length())
                    parent.deleteOneRightOf(GeneralEntry.this);
                return super.deletePreviousChar();
            }
        };
        this.nodes = FXCollections.observableArrayList(textField);
        this.autoComplete = new AutoComplete(textField, this::getSuggestions);
    }

    private Pair<String, String> splitAt(int index)
    {
        return new Pair<>(textField.getText().substring(0, index), textField.getText().substring(index + 1));
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    @Override
    public void deleteOneFromEnd()
    {
        int textLen = textField.getText().length();
        if (textLen > 0)
            textField.replaceText(textLen - 1, textLen, "");
        if (textField.getText().isEmpty())
            parent.replace(this, null);
    }

    @Override
    public void deleteOneFromBegin()
    {
        if (textField.getText().length() > 0)
            textField.replaceText(0, 1, "");
        if (textField.getText().isEmpty())
            parent.replace(this, null);
    }

    private List<Completion> getSuggestions(String text) throws UserException, InternalException
    {
        ArrayList<Completion> r = new ArrayList<>();

        r.add(new KeyShortcutCompletion("@", "Column Value"));

        @Nullable DataType t = parent.getType(this);
        if (t != null)
        {
            t.apply(new DataTypeVisitor<UnitType>()
            {

                @Override
                public UnitType number(NumberInfo displayInfo) throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }

                @Override
                public UnitType text() throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }

                @Override
                public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }

                @Override
                public UnitType bool() throws InternalException, UserException
                {
                    // It's common they might want to follow with <, =, etc, so
                    // we show all completions:
                    addAllFunctions(r);
                    r.add(new SimpleCompletion("true"));
                    r.add(new SimpleCompletion("false"));
                    return UnitType.UNIT;
                }

                @Override
                public UnitType tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }
            });
        }
        r.removeIf(c -> !c.shouldShow(text));
        return r;
    }

    private void addAllFunctions(ArrayList<Completion> r)
    {
        for (FunctionDefinition function : FunctionList.FUNCTIONS)
        {
            r.add(new FunctionCompletion(function));
        }
    }

    public ExpressionNode focusWhenShown()
    {
        Utility.onNonNull(textField.sceneProperty(), scene -> textField.requestFocus());
        return this;
    }
}
