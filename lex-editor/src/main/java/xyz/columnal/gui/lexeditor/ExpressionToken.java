package records.gui.lexeditor;

import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * An interface for keywords and operators, to get their content and their styled representation.
 */
@OnThread(Tag.Any)
public interface ExpressionToken extends StyledShowable
{
    // Gets the actual content for the expression
    public String getContent();

    default StyledString toStyledString()
    {
        return StyledString.s(getContent());
    }
}
