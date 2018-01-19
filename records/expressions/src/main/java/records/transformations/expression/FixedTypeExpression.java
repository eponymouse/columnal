package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.FixedTypeNode;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.loadsave.OutputBuilder;
import records.types.TypeExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An expression, and a fixed type it should conform to.  Like the :: operator in Haskell expressions,
 * (or like the `asTypeOf` function if you specified a type).  Just wraps an inner expression with a type. 
 */
public class FixedTypeExpression extends NonOperatorExpression
{
    // If a String, then it's an incomplete or incorrect type as specified in the slot.
    private final Either<String, DataType> type;
    private final Expression inner;
    
    public FixedTypeExpression(Either<String, DataType> type, Expression innerExpression)
    {
        this.type = type;
        this.inner = innerExpression;
    }

    public static Expression fixType(DataType fix, Expression expression)
    {
        if (expression instanceof FixedTypeExpression)
            return new FixedTypeExpression(Either.right(fix), ((FixedTypeExpression)expression).inner);
        else
            return new FixedTypeExpression(Either.right(fix), expression);
    }

    @Override
    public @Nullable @Recorded TypeExp check(RecordSet data, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable TypeExp innerType = inner.check(data, typeState, onError);
        if (innerType == null)
            return null;
        else
        {
            @NonNull TypeExp innerTypeFinal = innerType;
            return type.<@Nullable @Recorded TypeExp>eitherInt(text -> null, t -> onError.recordTypeAndError(this, TypeExp.unifyTypes(TypeExp.fromConcrete(this, t), innerTypeFinal)));
        }
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return inner.getValue(rowIndex, state);
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return inner.allColumnNames();
    }

    @Override
    public String save(boolean topLevel)
    {
        try
        {
            return "@type {|" + type.eitherInt(
                text -> "@incomplete \"" + GrammarUtility.escapeChars(text)+ "\"",
                t -> t.save(new OutputBuilder()).toString()) + "|} " + inner.save(false);
        }
        catch (InternalException e)
        {
            Utility.report(e);
            // Not much else we can do:
            return inner.save(false);
        }
    }
    
    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        return inner.toSolver(formulaManager, src, columnVariables);
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new FixedTypeNode(p, s, type.either(str -> str, t -> t.toString()), inner);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        // TODO
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        // TODO
        return null;
    }


    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixedTypeExpression that = (FixedTypeExpression) o;
        return Objects.equals(type, that.type) &&
            Objects.equals(inner, that.inner);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, inner);
    }

    public Either<String, DataType> getType()
    {
        return type;
    }

    public Expression getInner()
    {
        return inner;
    }
}
