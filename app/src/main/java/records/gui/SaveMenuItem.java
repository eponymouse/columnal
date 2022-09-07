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
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.TranslationUtility;

import java.time.Instant;

final class SaveMenuItem extends MenuItem
{
    private final ObjectProperty<Object> dummyNowBinding = new SimpleObjectProperty<>(new Object());
    private final @OnThread(Tag.FXPlatform) StringBinding text;

    @OnThread(Tag.FXPlatform)
    public SaveMenuItem(View view)
    {
        text = Bindings.createStringBinding(() ->
        {
            @Nullable Instant lastSave = view.lastSaveTime().get();
            if (lastSave == null)
                return TranslationUtility.getString("menu.project.modified");
            else
                return TranslationUtility.getString("menu.project.save", "" + (Instant.now().getEpochSecond() - lastSave.getEpochSecond()));
        }, view.lastSaveTime(), dummyNowBinding);
        // Invalidating this binding on show will force re-evaluation of the time gap:
        FXUtility.onceNotNull(parentMenuProperty(), menu -> menu.addEventHandler(Menu.ON_SHOWING, e -> {
            text.invalidate();
        }));
        textProperty().bind(text);
        setOnAction(e -> view.save(true));
    }


}