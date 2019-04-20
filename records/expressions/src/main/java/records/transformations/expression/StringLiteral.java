package records.transformations.expression;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.loadsave.OutputBuilder;
import records.typeExp.TypeExp;
import styled.CommonStyles;
import styled.StyledString;
import utility.Either;
import utility.Pair;

/**
 * Created by neil on 25/11/2016.
 */
public class StringLiteral extends Literal
{
    // The actual String value, without any escapes.
    private final @Value String value;

    public StringLiteral(String value)
    {
        this.value = DataTypeUtility.value(value);
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
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return OutputBuilder.quoted(value);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s("\"" + value + "\"").withStyle(CommonStyles.MONOSPACE), this);
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

    // Escapes the characters ready for editing.
    @Override
    public String editString()
    {
        return GrammarUtility.escapeChars(value);
    }
}
