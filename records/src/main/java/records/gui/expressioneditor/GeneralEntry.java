package records.gui.expressioneditor;

import com.sun.javafx.scene.control.behavior.TextFieldBehavior;
import com.sun.javafx.scene.control.skin.TextFieldSkin;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.textfield.AutoCompletionBinding.ISuggestionRequest;
import org.controlsfx.control.textfield.TextFields;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.unit.Unit;
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
import java.util.Arrays;
import java.util.Collections;
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
                    Pair<ExpressionNode, ExpressionNode> split = splitAt(operator);
                    //parent.replace(GeneralEntry.this, new DivideExpressionNode(split.getFirst(), split.getSecond()));
                }
                if (getText().startsWith("@"))
                {
                    // Turn into column reference
                    parent.replace(GeneralEntry.this, new AtColumn(getText().substring(1), parent).focusWhenShown());
                }
            }
        };
        this.nodes = FXCollections.observableArrayList(textField);
        this.autoComplete = new AutoComplete(textField, this::getSuggestions);
    }

    @OnThread(Tag.FX)
    private static Pair<ExpressionNode, ExpressionNode> splitAt(int index)
    {
        throw new RuntimeException("TODO");
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
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
