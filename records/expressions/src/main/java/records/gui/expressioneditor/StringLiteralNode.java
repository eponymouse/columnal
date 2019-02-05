package records.gui.expressioneditor;

import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.transformations.expression.Expression;
import records.transformations.expression.QuickFix;
import styled.StyledString;
import utility.gui.FXUtility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 20/12/2016.
 */
public final class StringLiteralNode extends SimpleLiteralNode
{
    public StringLiteralNode(String initialValue, ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        super(parent, Expression.class, "\"");

        FXUtility.sizeToFit(textField, 3.0, 3.0);
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text -> parent.changed(this));
        textField.setText(initialValue);
        updateNodes();
    }

    @Override
    public void save(ExpressionSaver saver)
    {
        saver.saveOperand(new records.transformations.expression.StringLiteral(GrammarUtility.processEscapes(textField.getText(), false)), this, this, c -> {});
    }

    @Override
    public boolean deleteLast()
    {
        return false;
    }

    @Override
    public boolean deleteFirst()
    {
        return false;
    }
}
