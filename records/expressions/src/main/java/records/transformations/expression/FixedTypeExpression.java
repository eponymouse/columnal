package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.FixedTypeNode;
import records.gui.expressioneditor.OperandNode;
import records.loadsave.OutputBuilder;
import records.transformations.expression.type.TypeExpression;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

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
    // This may a type expression that doesn't save to a valid type:
    private final TypeExpression type;
    private final @Recorded Expression inner;
    
    public FixedTypeExpression(@Recorded TypeExpression type, @Recorded Expression innerExpression)
    {
        this.type = type;
        this.inner = innerExpression;
    }

    public static Expression fixType(DataType fix, @Recorded Expression expression) throws InternalException
    {
        TypeExpression typeExpression = TypeExpression.fromDataType(fix);
        if (expression instanceof FixedTypeExpression)
        {
            return new FixedTypeExpression(typeExpression, ((FixedTypeExpression)expression).inner);
        }
        else
            return new FixedTypeExpression(typeExpression, expression);
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable TypeExp innerType = inner.check(dataLookup, typeState, onError);
        if (innerType == null)
            return null;
        else
        {
            @NonNull TypeExp innerTypeFinal = innerType;
            @Nullable DataType dataType = type.toDataType(typeState.getTypeManager());
            if (dataType == null)
            {
                // TODO should we record an error here?
                return null;
            }
            return onError.recordTypeAndError(this, TypeExp.unifyTypes(TypeExp.fromConcrete(this, dataType), innerTypeFinal));
        }
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return inner.getValue(rowIndex, state);
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return inner.allColumnReferences();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@type {|" + type.save(renames) + "|} " + inner.save(BracketedStatus.MISC, renames);
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        return StyledString.concat(StyledString.s("type ("), type.toStyledString(), StyledString.s(") "), inner.toDisplay(BracketedStatus.MISC));
    }
    
    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        return inner.toSolver(formulaManager, src, columnVariables);
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new FixedTypeNode(p, s, type, inner);
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

    public TypeExpression getType()
    {
        return type;
    }

    public Expression getInner()
    {
        return inner;
    }
}
