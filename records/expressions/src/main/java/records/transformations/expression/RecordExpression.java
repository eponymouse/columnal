package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.visitor.ExpressionVisitor;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Utility.RecordMap;
import utility.Utility.TransparentBuilder;

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordExpression extends Expression
{
    // Has to be list of pairs to maintain the same order:
    // Also, duplicates are a type error not a syntax error, so duplicates are possible here.
    private final ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members;

    public RecordExpression(ImmutableList<Pair<@ExpressionIdentifier String, @Recorded Expression>> members)
    {
        this.members = members;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        return null;
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        TransparentBuilder<ValueResult> valuesBuilder = new TransparentBuilder<>(members.size());
        // If it typechecked, assume no duplicate fields
        HashMap<@ExpressionIdentifier String, @Value Object> fieldValues = new HashMap<>();

        for (Pair<@ExpressionIdentifier String, @Recorded Expression> member : members)
        {
            fieldValues.put(member.getFirst(), valuesBuilder.add(member.getSecond().calculateValue(state)).value);
        }
        
        return result(DataTypeUtility.value(new RecordMap(fieldValues)), state, valuesBuilder.build());
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.record(this, members);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.of();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordExpression that = (RecordExpression) o;
        return members.equals(that.members);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(members);
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
         return expressionStyler.styleExpression(StyledString.roundBracket(members.stream().map(s -> StyledString.concat(StyledString.s(s.getFirst() + ": "), s.getSecond().toStyledString())).collect(StyledString.joining(", "))), this);
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "(" + members.stream().map(m -> m.getFirst() + ": " + m.getSecond().save(structured, BracketedStatus.NEED_BRACKETS, renames)).collect(Collectors.joining(", ")) + ")";
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new RecordExpression(Utility.<Pair<@ExpressionIdentifier String, Expression>, Pair<@ExpressionIdentifier String, Expression>>mapListI(members, (Pair<@ExpressionIdentifier String, Expression> p) -> p.mapSecond(e -> e.replaceSubExpression(toReplace, replaceWith))));
    }
}
