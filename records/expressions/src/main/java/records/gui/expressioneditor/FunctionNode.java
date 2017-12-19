package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.Expression;
import records.transformations.function.FunctionGroup;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 19/12/2016.
 */
public class FunctionNode extends SurroundNode implements ExpressionNodeParent
{
    // If it is known, it is Right(definition).  If unknown, Left(name)
    private Either<String, FunctionGroup> function;

    @SuppressWarnings("initialization") // Because LeaveableTextField gets marked uninitialized
    public FunctionNode(Either<String, FunctionGroup> function, ExpressionNodeParent semanticParent, @Nullable Expression argumentsExpression, ConsecutiveBase<Expression, ExpressionNodeParent> parent)
    {
        super(parent, semanticParent, "function", TranslationUtility.getString("function"), function.either(n -> n, FunctionGroup::getName), true, argumentsExpression);
        this.function = function;
    }

    @Override
    public void prompt(String prompt)
    {
        // Ignore
    }

    @SuppressWarnings("nullness") // contents is known to be non-null.
    @Override
    public Expression save(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        // TODO allow units (second optional consecutive)
        Expression argExp = contents.save(errorDisplayer, onError);
        return errorDisplayer.record(this, new CallExpression(head.getText(), function.either(n -> null, f -> f), Collections.emptyList(), argExp));
    }

    //@Override
    //public @Nullable DataType getType(EEDisplayNode child)
    //{
        // Not valid for multiple args anyway
        //return null;
    //}

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        if (function.isLeft())
            return Collections.emptyList();

        return Utility.mapList(function.getRight().getLikelyArgTypes(getEditor().getTypeManager().getUnitManager()), t -> new Pair<>(t, Collections.emptyList()));
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(@UnknownInitialization EEDisplayNode child)
    {
        return semanticParent.getAvailableVariables(this);
    }

    @Override
    public boolean canDeclareVariable(@UnknownInitialization EEDisplayNode chid)
    {
        return false;
    }
}
