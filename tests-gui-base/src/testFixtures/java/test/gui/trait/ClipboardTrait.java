package test.gui.trait;

import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.testjavafx.FxRobotInterface;
import test.gui.TFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;

public interface ClipboardTrait extends FxRobotInterface
{
    // Return is guaranteed to actually be a new clipboard entry
    @OnThread(Tag.Simulation)
    public default String copyToClipboard()
    {
        final String BLANK_CLIPBOARD = "@TEST";
        // Clear clipboard to give a blank slate to compare against:
        TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, BLANK_CLIPBOARD)));
        // Check original has the right string:
        push(KeyCode.F11);
        

        String r;
        int count = 80;
        do
        {
            r = TFXUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
            TFXUtil.sleep(100);
            count--;
        }
        while (BLANK_CLIPBOARD.equals(r) && count >= 0);
        assertThat(r, Matchers.not(BLANK_CLIPBOARD));
        return r;
    }
}
