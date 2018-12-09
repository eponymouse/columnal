package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import records.gui.expressioneditor.ExpressionInfoDisplay.CaretSide;
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import records.transformations.expression.QuickFix;
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
    public void addErrorAndFixes(StyledString error, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes);
    
    public void showType(String type);

    public boolean isShowingError();
    
    public void cleanup();

    public void clearAllErrors();

    public void saved();
}
