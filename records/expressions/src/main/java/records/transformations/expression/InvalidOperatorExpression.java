package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.scene.text.Text;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import styled.StyledString;
import styled.StyledString.Builder;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An expression with mixed operators, which make it invalid.  Can't be run, but may be
 * used while editing and for loading/saving invalid expressions.
 */
public class InvalidOperatorExpression extends NonOperatorExpression
{
    private final ImmutableList<@Recorded Expression> items;

    public InvalidOperatorExpression(ImmutableList<@Recorded Expression> operands)
    {
        this.items = operands;
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // Error should have been recorded elsewhere
        //onError.recordError(this, StyledString.s("Mixed or invalid operators in expression"));
        return null; // Invalid expressions can't type check
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Cannot get value for invalid expression");
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        if (structured)
            return "@invalidops(" + items.stream().map(x -> x.save(structured, BracketedStatus.NEED_BRACKETS, renames)).collect(Collectors.joining(", "))+ ")";
        else
            return items.stream().map(x -> x.save(structured, BracketedStatus.NEED_BRACKETS, renames)).collect(Collectors.joining(""));
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
        
        InvalidOperatorExpression that = (InvalidOperatorExpression) o;

        return items.equals(that.items);
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    @Override
    protected StyledString toDisplay(BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        Builder r = StyledString.builder();

        for (Expression item : items)
        {
            r.append(item.toDisplay(BracketedStatus.NEED_BRACKETS, expressionStyler));
        }
        
        class ErrorStyle extends Style<ErrorStyle>
        {
            protected ErrorStyle()
            {
                super(ErrorStyle.class);
            }

            @Override
            protected @OnThread(Tag.FXPlatform) void style(Text t)
            {
                t.getStyleClass().add("expression-error");
            }

            @Override
            protected ErrorStyle combine(ErrorStyle with)
            {
                return this;
            }

            @Override
            protected boolean equalsStyle(ErrorStyle item)
            {
                return item instanceof ErrorStyle;
            }
        }
        
        return expressionStyler.styleExpression(r.build(StyledString.s(" ")).withStyle(new ErrorStyle()), this);
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new InvalidOperatorExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }

    public ImmutableList<@Recorded Expression> _test_getItems()
    {
        return items;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.invalidOps(this, items);
    }
}
