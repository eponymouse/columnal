package records.gui.expressioneditor;

import com.sun.org.apache.xpath.internal.compiler.PsuedoNames;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.FunctionCompletion;
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

    private final VBox container;

    public static enum Status
    {
        COLUMN_REFERENCE, TAG, LITERAL /*number or bool, not string */, VARIABLE, FUNCTION, UNFINISHED;
    }

    private final TextField textField;
    private final Label typeLabel;
    private ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.UNFINISHED);
    private final ObservableList<Node> nodes;
    private final AutoComplete autoComplete;

    @SuppressWarnings("initialization")
    public GeneralEntry(String content, ExpressionParent parent)
    {
        super(parent);
        this.textField = new TextField(content);
        textField.getStyleClass().add("entry-field");
        Utility.sizeToFit(textField);
        typeLabel = new Label();
        typeLabel.getStyleClass().add("entry-type");
        container = new VBox(typeLabel, textField);
        container.getStyleClass().add("entry");
        this.nodes = FXCollections.observableArrayList(container);
        this.autoComplete = new AutoComplete(textField, this::getSuggestions, c -> {
            textField.setText(c.getCompletedText());
            status.setValue(c.getType());
            parent.addToRight(this, new GeneralEntry("", parent).focusWhenShown());
        });

        Utility.addChangeListenerPlatformNN(status, s -> {
            for (Status possibleStatus : Status.values())
            {
                container.pseudoClassStateChanged(getPseudoClass(possibleStatus), possibleStatus == s);
            }
            typeLabel.setText(getTypeLabel(s));
        });
        Utility.addChangeListenerPlatformNN(textField.textProperty(), t -> {
            textField.pseudoClassStateChanged(PseudoClass.getPseudoClass("ps-empty"), t.isEmpty());
        });
        textField.pseudoClassStateChanged(PseudoClass.getPseudoClass("ps-empty"), textField.getText().isEmpty());
    }

    private String getTypeLabel(Status s)
    {
        switch (s)
        {
            case COLUMN_REFERENCE:
                return "column";
            case TAG:
                return "tag";
            case LITERAL:
                return "";
            case VARIABLE:
                return "variable";
            case FUNCTION:
                return "call";
            case UNFINISHED:
                return "error";
        }
        return "";
    }

    private PseudoClass getPseudoClass(Status s)
    {
        String pseudoClass;
        switch (s)
        {
            case COLUMN_REFERENCE:
                pseudoClass = "ps-column";
                break;
            case TAG:
                pseudoClass = "ps-tag";
                break;
            case LITERAL:
                pseudoClass = "ps-literal";
                break;
            case VARIABLE:
                pseudoClass = "ps-variable";
                break;
            case FUNCTION:
                pseudoClass = "ps-function";
                break;
            default:
                pseudoClass = "ps-unfinished";
                break;
        }
        return PseudoClass.getPseudoClass(pseudoClass);
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    private List<Completion> getSuggestions(String text) throws UserException, InternalException
    {
        ArrayList<Completion> r = new ArrayList<>();
        addAllFunctions(r);
        r.add(new SimpleCompletion("true", Status.LITERAL));
        r.add(new SimpleCompletion("false", Status.LITERAL));
        for (ColumnId columnId : parent.getAvailableColumns())
        {
            r.add(new SimpleCompletion(columnId.getRaw(), Status.COLUMN_REFERENCE));
        }
        // TODO: use type to prioritise, but not to filter (might always be followed by
        //   ? to do pattern match)
        /*
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

                    return UnitType.UNIT;
                }

                @Override
                public UnitType tagged(String typeName, List<TagType<DataType>> tags) throws InternalException, UserException
                {
                    return UnitType.UNIT;
                }
            });
        }
        */
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
