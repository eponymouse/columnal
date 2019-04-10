package records.gui.expressioneditor;

import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

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
