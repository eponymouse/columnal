/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

import java.util.List;
import java.util.Optional;

@OnThread(Tag.FXPlatform)
public class LexAutoComplete
{
    @OnThread(Tag.FXPlatform)
    public interface EditorDisplayInterface
    {
        Node asNode();
        
        public @CanonicalLocation int getCaretPosition();

        public @Nullable Point2D getCaretBottomOnScreen(@CanonicalLocation int caretPos);
    }
    
    private final LexAutoCompleteWindow window;
    private final EditorDisplayInterface editor;
    private final Timeline updatePosition;

    // When the user clicks on the code completion, they can sometimes click more than is needed, especially using
    // double-click to select an item, when actually single-click on an already-selected item is enough.  This can cause
    // issues with the click falling through to the item beneath, which they didn't intend.  So we add "click immunity";
    // for a short period of time after the click that dismisses code completion, further clicks won't register on the
    // item beneath.  This value is for comparing to System.currentTimeMillis()
    private long clickImmuneUntil = -1L;

    public LexAutoComplete(@UnknownInitialization EditorDisplayInterface editor, LexCompletionListener triggerCompletion)
    {
        this.window = new LexAutoCompleteWindow(triggerCompletion);
        this.editor = Utility.later(editor);
        this.updatePosition = new Timeline(new KeyFrame(Duration.millis(250), e -> {
            Utility.later(this).updateWindowPosition(Utility.later(this).window.listView.getItems().collect(ImmutableList.<LexCompletion>toImmutableList()));
        }));
    }

    public void show(ImmutableList<LexCompletionGroup> groups)
    {
        window.setCompletions(groups);
        FXUtility.runAfterNextLayout(window.getScene(), () -> updateWindowPosition(window.listView.getItems().collect(ImmutableList.<LexCompletion>toImmutableList())));
        updatePosition.playFromStart();
    }

    private void updateWindowPosition(List<LexCompletion> completions)
    {
        @SuppressWarnings("units") // Because it passes through IntStream
        Point2D caretBottom = editor.getCaretBottomOnScreen(completions.stream().mapToInt(c -> c.startPos).min().orElse(editor.getCaretPosition()));
        double labelPad = window.listView.getTotalTextLeftPad() + 1;
        if (caretBottom != null && !completions.isEmpty())
            window.show(editor.asNode(), caretBottom.getX() - labelPad, caretBottom.getY());
        else
            hide(false);
    }

    public void hide(boolean becauseOfMouseClick)
    {
        window.hide();
        window.setCompletions(ImmutableList.of());
        updatePosition.stop();
        if (becauseOfMouseClick)
        {
            clickImmuneUntil = System.currentTimeMillis() + 400;
        }
    }

    public boolean isShowing()
    {
        return window.isShowing();
    }

    public void down()
    {
        window.listView.down();
    }

    public void up()
    {
        window.listView.up();
    }

    public void pageDown()
    {
        window.listView.pageDown();
    }

    public void pageUp()
    {
        window.listView.pageUp();
    }

    public Optional<LexCompletion> getSelectedCompletion()
    {
        return Optional.ofNullable(window.listView.getSelectedItem());
    }

    public boolean isMouseClickImmune()
    {
        return System.currentTimeMillis() < clickImmuneUntil;
    }

    public void cleanup()
    {
        window.cleanup();
    }

    public enum LexSelectionBehaviour
    {
        SELECT_IF_ONLY,
        SELECT_IF_TOP,
        NO_AUTO_SELECT;
    }

}
