package records.transformations.expression;

import annotation.identifier.qual.UnitIdentifier;
import javafx.scene.Scene;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public interface FixHelper
{
    public void createNewUnit(@UnitIdentifier String newUnitName, Scene editorScene);
}
