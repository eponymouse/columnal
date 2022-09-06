package records.error;

import org.checkerframework.dataflow.qual.Pure;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

public class ExceptionWithStyle extends Exception
{
    private final StyledString styledMessage;
    
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

    @OnThread(Tag.Any)
    @Pure public final StyledString getStyledMessage()
    {
        return styledMessage;
    }
}
