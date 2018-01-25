package records.gui.expressioneditor;

import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.FixedTypeExpression;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.Collections;
import java.util.List;

public class FixedTypeNode extends SurroundNode
{
    public FixedTypeNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, String startingType, @Nullable Expression startingInner)
    {
        super(parent, semanticParent, "fix-type", TranslationUtility.getString("head.type"), startingType, true, startingInner);
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        return Collections.emptyList();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(@UnknownInitialization EEDisplayNode child)
    {
        return parent.getThisAsSemanticParent().getAvailableVariables(this);
    }

    @Override
    public boolean canDeclareVariable(EEDisplayNode chid)
    {
        return false;
    }

    @Override
    public void focusWhenShown()
    {
        // We focus the head, not the body like SurroundNode does:
        FXUtility.onceNotNull(head.sceneProperty(), s -> head.requestFocus());
    }

    @Override
    public void prompt(String prompt)
    {

    }

    @Override
    public Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {        
        @SuppressWarnings("nullness")
        Expression innerExp = contents.save(errorDisplayer, onError);
        Either<String, DataType> type = Either.left(head.getText());
        try
        {
            type = getEditor().getTypeManager().loadTypeUseAllowIncomplete(head.getText());
        }
        catch (InternalException e)
        {
            Utility.report(e);
        }
        catch (UserException e)
        {
            // No need to log, this is reasonably expected if the user hasn't completely filled in the type.
        }
        
        return new FixedTypeExpression(type, innerExp);
    }
}
