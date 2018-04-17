package records.transformations.expression.type;

import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public interface TypeParent
{
    public boolean isRoundBracketed();
}
