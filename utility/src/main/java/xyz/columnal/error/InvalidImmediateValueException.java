package xyz.columnal.error;

import xyz.columnal.styled.StyledString;

public class InvalidImmediateValueException extends UserException
{
    private final String invalid;

    public InvalidImmediateValueException(StyledString message, String invalid)
    {
        super(message);
        this.invalid = invalid;
    }

    public String getInvalid()
    {
        return invalid;
    }
}
