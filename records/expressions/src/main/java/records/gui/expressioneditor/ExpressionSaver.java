package records.gui.expressioneditor;

import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.gui.expressioneditor.GeneralExpressionEntry.Op;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;

public abstract class ExpressionSaver implements ErrorAndTypeRecorder
{
    class Context {}
    
    // Note: if we are copying to clipboard, callback will not be called
    public void saveKeyword(Keyword keyword, ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext) {}
    public void saveOperator(Op operator, ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext) {}
    public void saveOperand(Expression singleItem, ErrorDisplayer<Expression, ExpressionSaver> errorDisplayer, FXPlatformConsumer<Context> withContext) {}
    
    
    /**
     * Get likely types and completions for given child.  For example,
     * if the expression is column Name = _ (where the RHS
     * is the child in question) we might offer Text and most frequent values
     * of the Name column.
     *
     * The completions are not meant to be all possible values of the given
     * type (e.g. literals, available columns, etc), as that can be figured out
     * from the type regardless of context.  This is only items which make
     * particular sense in this particular context, e.g. a commonly passed argument
     * to a function.
     */
    //List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException;

    /**
     * Gets all special keywords available in child operators,
     * e.g. "then", paired with their description.
     */
    //default ImmutableList<Pair<String, @Localized String>> operatorKeywords()
    //{
    //    return ImmutableList.of();
    //}

    /**
     * Can this direct child node declare a variable?  i.e. is it part of a pattern?
     */
    //boolean canDeclareVariable(EEDisplayNode chid);
}
