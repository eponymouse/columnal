package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.FunctionNode;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperandOps;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionDefinition.FunctionTypes;
import records.transformations.function.FunctionList;
import records.types.TupleTypeExp;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 11/12/2016.
 */
public class CallExpression extends NonOperatorExpression
{
    private final String functionName;
    private final Expression param;
    private final List<Unit> units;
    // If null, function doesn't exist
    private final @Nullable FunctionDefinition definition;
    // If null, didn't type check
    @MonotonicNonNull
    private FunctionTypes types;

    public CallExpression(String functionName, @Nullable FunctionDefinition functionDefinition, List<Unit> units, @Recorded Expression arg)
    {
        this.functionName = functionName;
        this.definition = functionDefinition;
        this.units = new ArrayList<>(units);
        this.param = arg;
    }
    
    // Used for testing, and for creating quick recipe functions:
    @SuppressWarnings("recorded")
    public CallExpression(UnitManager mgr, String functionName, Expression... args) throws InternalException
    {
        this(functionName, FunctionList.lookup(mgr, functionName), Collections.emptyList(), args.length == 1 ? args[0] : new TupleExpression(ImmutableList.copyOf(args)));
    }

    @Override
    public @Nullable @Recorded TypeExp check(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        if (definition == null)
        {
            onError.recordError(this, StyledString.s("Unknown function: \"" + functionName + "\""));
            return null;
        }

        @Nullable TypeExp paramType = param.check(data, state, onError);
        if (paramType == null)
            return null;
        types = definition.makeParamAndReturnType(state.getTypeManager());
        boolean takesList = TypeExp.isList(types.paramType);
        Either<StyledString, TypeExp> temp = TypeExp.unifyTypes(types.paramType, paramType);
        @Nullable TypeExp checked = onError.recordError(this, temp);
        if (checked == null)
        {
            // Check after unification attempted, because that will have constrained
            // to list if possible (and not, if not)
            if (takesList)
            {
                TypeExp prunedParam = paramType.prune();

                if (!TypeExp.isList(prunedParam) && param instanceof ColumnReference && ((ColumnReference)param).getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW)
                {
                    ColumnReference colRef = (ColumnReference)param;
                    @SuppressWarnings("recorded")
                    CallExpression replacementCall = new CallExpression(functionName, definition, units, new ColumnReference(colRef.getTableId(), colRef.getColumnId(), ColumnReferenceType.WHOLE_COLUMN));
                    // Offer to turn a this-row column reference into whole column:
                    onError.recordQuickFixes(this, Collections.singletonList(
                        new QuickFix<>("fix.wholeColumn", ReplacementTarget.CURRENT, replacementCall)
                    ));
                }
                if (prunedParam instanceof TupleTypeExp && param instanceof TupleExpression)
                {
                    // Offer to turn tuple into a list:
                    Expression replacementParam = new ArrayExpression(((TupleExpression)param).getMembers());
                    @SuppressWarnings("recorded")
                    CallExpression replacementCall = new CallExpression(functionName, definition, units, replacementParam);
                    onError.recordQuickFixes(this, Collections.singletonList(
                            new QuickFix<>("fix.squareBracketAs", ReplacementTarget.CURRENT, replacementCall)
                    ));
                }
                // Although we may want to pass a tuple as a single-item list, it's much less likely
                // than the missing list brackets case, hence the else here:
                else if (!TypeExp.isList(prunedParam))
                {
                    // Offer to make a list:
                    @SuppressWarnings("recorded")
                    Expression replacementParam = new ArrayExpression(ImmutableList.of(param));
                    @SuppressWarnings("recorded")
                    CallExpression replacementCall = new CallExpression(functionName, definition, units, replacementParam);
                    onError.recordQuickFixes(this, Collections.singletonList(
                        new QuickFix<>("fix.singleItemList", ReplacementTarget.CURRENT, replacementCall)
                    ));
                }
                
                
            }
            
            return null;
        }
        return onError.recordType(this, types.returnType);
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        if (types == null)
            throw new InternalException("Calling function " + functionName + " which didn't typecheck");
        
        return types.getInstanceAfterTypeCheck().getValue(rowIndex, param.getValue(rowIndex, state));
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return param.allColumnNames();
    }

    @Override
    public String save(BracketedStatus surround)
    {
        if (param instanceof TupleExpression)
            return functionName + param.save(BracketedStatus.MISC);
        else
            return functionName + "(" + param.save(BracketedStatus.DIRECT_ROUND_BRACKETED) + ")";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        if (param instanceof TupleExpression)
            return StyledString.concat(StyledString.s(functionName), param.toDisplay(BracketedStatus.MISC));
        else
            return StyledString.concat(StyledString.s(functionName + "("), param.toDisplay(BracketedStatus.DIRECT_ROUND_BRACKETED), StyledString.s(")"));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        // If successfully type-checked:
        if (definition != null)
        {
            @NonNull FunctionDefinition definitionFinal = definition;
            return (p, s) -> new FunctionNode(Either.right(definitionFinal), s, param, p);
        }
        else
            return (p, s) -> new FunctionNode(Either.left(functionName), s, param, p);
    }

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return param._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newParam -> new CallExpression(functionName, definition, units, newParam)));
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        if (definition == null)
            throw new InternalException("Calling _test_typeFailure after type check failure");
        Pair<List<Unit>, Expression> badParams = definition._test_typeFailure(r, newExpressionOfDifferentType, unitManager);
        return new CallExpression(functionName, definition, badParams.getFirst(), badParams.getSecond());
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallExpression that = (CallExpression) o;

        if (!functionName.equals(that.functionName)) return false;
        return param.equals(that.param);
    }

    @Override
    public int hashCode()
    {
        int result = functionName.hashCode();
        result = 31 * result + param.hashCode();
        return result;
    }

    public String _test_getFunctionName()
    {
        return functionName;
    }

    public Expression _test_getParam()
    {
        return param;
    }
}
