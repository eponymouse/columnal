package records.gui.expressioneditor;

import records.transformations.expression.ErrorAndTypeRecorder;

import java.util.List;

/**
 * An interface implemented by an expression editor component which can display
 * an error and accompanying quick fixes.
 */
public interface ErrorDisplayer<EXPRESSION>
{
    // TODO make the String @Localized
    public void showError(String error, List<ErrorAndTypeRecorder.QuickFix<EXPRESSION>> quickFixes);
    
    public void showType(String type);

    public boolean isShowingError();
}
