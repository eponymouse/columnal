package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.BracketedExpression;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionList;
import records.types.MutVar;
import records.types.TupleTypeExp;
import records.types.TypeCons;
import records.types.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;
import utility.ValueFunction;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 11/12/2016.
 */
public class CallExpression extends Expression
{
    private final Expression function;
    private final Expression param;

    public CallExpression(Expression function, @Recorded Expression arg)
    {
        this.function = function;
        this.param = arg;
    }
    
    // Used for testing, and for creating quick recipe functions:
    // Creates a call to a named function
    @SuppressWarnings("recorded")
    public CallExpression(UnitManager mgr, String functionName, Expression... args)
    {
        this(nonNullLookup(mgr, functionName), args.length == 1 ? args[0] : new TupleExpression(ImmutableList.copyOf(args)));
    }

    private static Expression nonNullLookup(UnitManager mgr, String functionName)
    {
        try
        {
            FunctionDefinition functionDefinition = FunctionList.lookup(mgr, functionName);
            if (functionDefinition != null)
                return new StandardFunction(functionDefinition);
        }
        catch (InternalException e)
        {
            Log.log(e);
        }
        return new UnfinishedExpression(functionName, null);
    }

    @Override
    public @Nullable @Recorded TypeExp check(TableLookup dataLookup, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @Nullable TypeExp paramType = param.check(dataLookup, state, onError);
        if (paramType == null)
            return null;
        @Nullable TypeExp functionType = function.check(dataLookup, state, onError);
        if (functionType == null)
            return null;
        TypeExp returnType = new MutVar(this);
        TypeExp actualCallType = new TypeCons(this, TypeExp.CONS_FUNCTION, paramType, returnType);
        
        Either<StyledString, TypeExp> temp = TypeExp.unifyTypes(functionType, actualCallType);
        @Nullable TypeExp checked = onError.recordError(this, temp);
        if (checked == null)
        {
            // Check after unification attempted, because that will have constrained
            // to list if possible (and not, if not)
            boolean takesList = TypeExp.isList(paramType);
            if (takesList)
            {
                TypeExp prunedParam = paramType.prune();

                if (!TypeExp.isList(prunedParam) && param instanceof ColumnReference && ((ColumnReference)param).getReferenceType() == ColumnReferenceType.CORRESPONDING_ROW)
                {
                    ColumnReference colRef = (ColumnReference)param;
                    @SuppressWarnings("recorded")
                    CallExpression replacementCall = new CallExpression(function, new ColumnReference(colRef.getTableId(), colRef.getColumnId(), ColumnReferenceType.WHOLE_COLUMN));
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
                    CallExpression replacementCall = new CallExpression(function, replacementParam);
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
                    CallExpression replacementCall = new CallExpression(function, replacementParam);
                    onError.recordQuickFixes(this, Collections.singletonList(
                        new QuickFix<>("fix.singleItemList", ReplacementTarget.CURRENT, replacementCall)
                    ));
                }
                
                
            }
            
            return null;
        }
        return onError.recordType(this, returnType);
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        ValueFunction functionValue = Utility.cast(function.getValue(rowIndex, state), ValueFunction.class);
            
        return functionValue.call(param.getValue(rowIndex, state));
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return param.allColumnReferences();
    }

    @Override
    public String save(BracketedStatus surround, TableAndColumnRenames renames)
    {
        return function.save(BracketedStatus.MISC, renames) + "(" + param.save(BracketedStatus.DIRECT_ROUND_BRACKETED, renames) + ")";
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        return StyledString.concat(function.toDisplay(BracketedStatus.MISC), StyledString.s("("), param.toDisplay(BracketedStatus.DIRECT_ROUND_BRACKETED), StyledString.s(")"));
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    public SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>> loadAsSingle()
    {
        return (p, s) -> new BracketedExpression(ConsecutiveBase.EXPRESSION_OPS, p, SingleLoader.withSemanticParent(loadAsConsecutive(true), s), ')');
    }

    @Override
    public Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(
            ImmutableList.of(function.loadAsSingle(), (p, s) -> new BracketedExpression(ConsecutiveBase.EXPRESSION_OPS, p, SingleLoader.withSemanticParent(param.loadAsConsecutive(true), s), ')')),
            ImmutableList.of((p, s) -> new OperatorEntry<>(Expression.class, "", false, p))
        );
    }

    /*
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
            @Override
            
        }
    */
    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return param._test_allMutationPoints().map(p -> new Pair<>(p.getFirst(), newParam -> new CallExpression(function, newParam)));
    }

    @SuppressWarnings("recorded")
    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        Expression badParam = param._test_typeFailure(r, newExpressionOfDifferentType, unitManager);
        return badParam == null ? null : new CallExpression(function, badParam);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallExpression that = (CallExpression) o;

        if (!function.equals(that.function)) return false;
        return param.equals(that.param);
    }

    @Override
    public int hashCode()
    {
        int result = function.hashCode();
        result = 31 * result + param.hashCode();
        return result;
    }

    public Expression _test_getFunction()
    {
        return function;
    }

    public Expression _test_getParam()
    {
        return param;
    }
}
