package records.error;

import styled.StyledString;

public class ExceptionWithStyle extends Exception
{
    private StyledString styledMessage;
    
    protected ExceptionWithStyle(StyledString styledMessage)
    {
        super(styledMessage.toPlain());
        this.styledMessage = styledMessage;
    }

    public ExceptionWithStyle(StyledString styledMessage, Throwable e)
    {
        super(styledMessage.toPlain(), e);
        this.styledMessage = styledMessage;
    }

    public StyledString getStyledMessage()
    {
        return styledMessage;
    }
}
