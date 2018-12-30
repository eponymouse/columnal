package records.gui.flex;

import styled.StyledString;
import utility.Either;
import utility.Pair;

// An uneditable EditorKit that displays a simple label (e.g. error message)
public class EditorKitSimpleLabel<T> extends EditorKit<T>
{
    public EditorKitSimpleLabel(String label)
    {
        super(label, new Recogniser<T>()
        {
            @Override
            public Either<ErrorDetails, SuccessDetails<T>> process(ParseProgress src)
            {
                // Shouldn't be called anyway:
                return Either.left(new ErrorDetails(StyledString.s(label)));
            }
        }, (t, v) -> {}, () -> {});
    }
}
