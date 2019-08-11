package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.CommonStyles;
import styled.StyledString;

/**
 * Created by neil on 25/11/2016.
 */
public class StringLiteral extends Literal
{
    // The actual String value, without any remaining escapes.
    private final @Value String value;
    private final String rawUnprocessed;

    public StringLiteral(String rawUnprocessed)
    {
        this.rawUnprocessed = rawUnprocessed;
        this.value = DataTypeUtility.value(GrammarUtility.processEscapes(rawUnprocessed, false));
    }

    @Override
    protected @Nullable TypeExp checkType(TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError)
    {
        return TypeExp.text(this);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        return result(value, state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "\"" + rawUnprocessed + "\"";
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s("\"" + rawUnprocessed + "\"").withStyle(CommonStyles.MONOSPACE), this);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringLiteral that = (StringLiteral) o;

        return rawUnprocessed.equals(that.rawUnprocessed);
    }

    @Override
    public int hashCode()
    {
        return rawUnprocessed.hashCode();
    }

    // Escapes the characters ready for editing.
    @Override
    public String editString()
    {
        return rawUnprocessed;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.litText(this, rawUnprocessed);
    }
}
