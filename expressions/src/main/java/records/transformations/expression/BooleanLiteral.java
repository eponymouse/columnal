package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;

/**
 * Created by neil on 27/11/2016.
 */
public class BooleanLiteral extends Literal
{
    private final @Value Boolean value;

    public BooleanLiteral(boolean value)
    {
        this.value = DataTypeUtility.value(value);
    }

    @Override
    protected @Nullable TypeExp checkType(TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError)
    {
        return TypeExp.bool(this);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state)
    {
        return result(value, state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return Boolean.toString(value);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s(Boolean.toString(value)), this);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BooleanLiteral that = (BooleanLiteral) o;

        return value.equals(that.value);
    }

    @Override
    public int hashCode()
    {
        return (value ? 1 : 0);
    }

    @Override
    public String editString()
    {
        return value.toString();
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.litBoolean(this, value);
    }
}
