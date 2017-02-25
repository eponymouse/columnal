package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.Expression;

import java.util.IdentityHashMap;

/**
 * Created by neil on 24/02/2017.
 */
public class ErrorDisplayerRecord
{
    private final IdentityHashMap<Expression, ErrorDisplayer> displayers = new IdentityHashMap<>();

    @SuppressWarnings("initialization")
    public Expression record(@UnknownInitialization(Object.class) ErrorDisplayer displayer, Expression e)
    {
        displayers.put(e, displayer);
        return e;
    }

    public boolean showError(Expression e, String s)
    {
        @Nullable ErrorDisplayer d = displayers.get(e);
        if (d != null)
        {
            d.showError(s);
            return true;
        }
        else
            return false;
    }
}
