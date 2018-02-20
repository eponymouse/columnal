package records.gui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.time.Instant;

class DummySaveMenuItem extends MenuItem
{
    private final ObjectProperty<Object> dummyNowBinding = new SimpleObjectProperty<>(new Object());
    private final @OnThread(Tag.FXPlatform) StringBinding text;

    @OnThread(Tag.FXPlatform)
    public DummySaveMenuItem(View view)
    {
        setDisable(true);
        text = Bindings.createStringBinding(() ->
        {
            @Nullable Instant lastSave = view.lastSaveTime().get();
            if (lastSave == null)
                return TranslationUtility.getString("menu.project.modified");
            else
                return "" + (Instant.now().getEpochSecond() - lastSave.getEpochSecond());
        }, view.lastSaveTime(), dummyNowBinding);
        // Invalidating this binding on show will force re-evaluation of the time gap:
        FXUtility.onceNotNull(parentMenuProperty(), menu -> menu.addEventHandler(Menu.ON_SHOWING, e -> {
            text.invalidate();
        }));
        textProperty().bind(TranslationUtility.bindString("menu.project.save", text));
    }


}
