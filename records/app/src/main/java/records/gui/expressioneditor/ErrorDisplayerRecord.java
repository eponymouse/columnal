package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ErrorRecorder;
import records.transformations.expression.Expression;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Created by neil on 24/02/2017.
 */
public class ErrorDisplayerRecord<EXPRESSION>
{
    private final IdentityHashMap<EXPRESSION, ErrorDisplayer> displayers = new IdentityHashMap<>();

    @SuppressWarnings("initialization")
    public @NonNull EXPRESSION record(@UnknownInitialization(Object.class) ErrorDisplayer displayer, @NonNull EXPRESSION e)
    {
        displayers.put(e, displayer);
        return e;
    }

    public boolean showError(EXPRESSION e, String s, List<ErrorRecorder.QuickFix> quickFixes)
    {
        @Nullable ErrorDisplayer d = displayers.get(e);
        if (d != null)
        {
            d.showError(s, quickFixes);
            return true;
        }
        else
            return false;
    }
}
