package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.StringLiteralNode;
import records.loadsave.OutputBuilder;
import records.types.TypeExp;
import styled.CommonStyles;
import styled.StyledString;

/**
 * Created by neil on 25/11/2016.
 */
public class StringLiteral extends Literal
{
    private final @Value String value;

    public StringLiteral(String value)
    {
        this.value = DataTypeUtility.value(value);
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws InternalException
    {
        return onError.recordTypeNN(this, TypeExp.text(this));
    }

    @Override
    public @Value Object getValue(EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return OutputBuilder.quoted(value);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus)
    {
        return StyledString.s("\"" + value + "\"").withStyle(CommonStyles.MONOSPACE);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringLiteral that = (StringLiteral) o;

        return value.equals(that.value);
    }

    @Override
    public int hashCode()
    {
        return value.hashCode();
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new StringLiteralNode(editString(), p);
    }

    @Override
    public String editString()
    {
        return value;
    }

    public String getRawString()
    {
        return value;
    }
}
