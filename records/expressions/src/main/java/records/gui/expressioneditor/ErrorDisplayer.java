package records.gui.expressioneditor;

import records.transformations.expression.ErrorAndTypeRecorder;
import styled.StyledShowable;
import styled.StyledString;

import java.util.List;

/**
 * An interface implemented by an expression editor component which can display
 * an error and accompanying quick fixes.
 */
public interface ErrorDisplayer<EXPRESSION extends StyledShowable, SEMANTIC_PARENT>
{
    // TODO make the String @Localized
    public void addErrorAndFixes(StyledString error, List<ErrorAndTypeRecorder.QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes);
    
    public void showType(String type);

    public boolean isShowingError();
    
    public void cleanup();

    public void clearAllErrors();
}
