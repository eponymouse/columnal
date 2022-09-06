package xyz.columnal.error;

import xyz.columnal.styled.StyledString;

/**
 * Created by neil on 22/10/2016.
 */
public class InternalException extends ExceptionWithStyle
{
    public InternalException(String message)
    {
        super(StyledString.s(message));
    }

    public InternalException(String message, Exception e)
    {
        super(StyledString.s(message), e);
    }
}
