package records.gui.expressioneditor;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.Expression;
import records.transformations.expression.QuickFix;
import records.transformations.expression.TemporalLiteral;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.stream.Stream;

public final class TemporalLiteralNode extends SimpleLiteralNode
{
    private final DateTimeType dateTimeType;

    public TemporalLiteralNode(ConsecutiveBase<Expression, ExpressionSaver> parent, DateTimeType dateTimeType, @Nullable String initialContent)
    {
        super(parent, Expression.class, "}");
        this.dateTimeType = dateTimeType;
        if (initialContent != null)
            textField.setText(initialContent);
        updateNodes();
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

    @Override
    public void save(ExpressionSaver saver)
    {
        saver.saveOperand(new TemporalLiteral(dateTimeType, textField.getText().trim()), this, this, c -> {});
    }

    @Override
    public boolean opensBracket(BracketBalanceType bracketBalanceType)
    {
        return false;
    }

    @Override
    public boolean closesBracket(BracketBalanceType bracketBalanceType)
    {
        return false;
    }

    @Override
    public void setText(String initialContent)
    {
        textField.setText(initialContent);
    }

    @Override
    public void showType(String type)
    {

    }
}
