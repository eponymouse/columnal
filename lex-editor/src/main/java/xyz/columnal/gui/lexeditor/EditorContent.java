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

package xyz.columnal.gui.lexeditor;

import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import javafx.scene.Node;
import javafx.scene.text.Text;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import xyz.columnal.gui.lexeditor.Lexer.LexerResult;
import xyz.columnal.gui.lexeditor.Lexer.LexerResult.CaretPos;
import xyz.columnal.gui.lexeditor.TopLevelEditor.DisplayType;
import xyz.columnal.gui.lexeditor.TopLevelEditor.Focus;
import xyz.columnal.gui.lexeditor.completion.InsertListener;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;

public final class EditorContent<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext> implements InsertListener
{
    private LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> curContent;
    private @CanonicalLocation int curCaretPosition;
    private @CanonicalLocation int curAnchorPosition;
    private final Lexer<EXPRESSION, CODE_COMPLETION_CONTEXT> lexer;
    private final ArrayList<CaretPositionListener> caretPositionListeners = new ArrayList<>();
    private final ArrayList<FXPlatformRunnable> contentListeners = new ArrayList<>();
    private final UndoManager undoManager;
    
    @SuppressWarnings("units")
    public EditorContent(String originalContent, Lexer<EXPRESSION, CODE_COMPLETION_CONTEXT> lexer)
    {
        this.lexer = lexer;
        this.curContent = this.lexer.process(originalContent, 0, Utility.later(this));
        this.curCaretPosition = curContent.caretPositions.size() > 0 ? curContent.caretPositions.get(0).positionInternal : 0;
        this.curAnchorPosition = curCaretPosition;
        this.undoManager = new UndoManager(originalContent);
        contentListeners.add(() -> undoManager.contentChanged(getText(), getCaretPosition()));
    }

    public void positionCaret(Focus side)
    {
        if (side == Focus.LEFT && curContent.caretPositions.size() > 0)
            positionCaret(curContent.caretPositions.get(0).positionInternal, true);
        else if (side == Focus.RIGHT && curContent.caretPositions.size() > 0)
            positionCaret(curContent.caretPositions.get(curContent.caretPositions.size() - 1).positionInternal, true);
    }
    
    public void positionCaret(@CanonicalLocation int pos, boolean alsoSetAnchor)
    {
        curCaretPosition = pos;
        if (alsoSetAnchor)
            curAnchorPosition = pos;
        notifyCaretPositionListeners(CaretMoveReason.CARET_MOVED);
    }

    void notifyCaretPositionListeners(CaretMoveReason reason)
    {
        for (CaretPositionListener caretPositionListener : caretPositionListeners)
        {
            caretPositionListener.caretMoved(curCaretPosition, reason);
        }
    }

    public @CanonicalLocation int getAnchorPosition()
    {
        return curAnchorPosition;
    }

    public @CanonicalLocation int getCaretPosition()
    {
        return curCaretPosition;
    }

    public void replaceSelection(String content)
    {
        replaceSelection(content, false);
    }
    
    @SuppressWarnings("units") // Because of min and max
    public void replaceSelection(String content, boolean putCaretAtLeadingEdge)
    {
        replaceText(Math.min(curCaretPosition, curAnchorPosition), Math.max(curCaretPosition, curAnchorPosition), content, putCaretAtLeadingEdge ? curCaretPosition : null);
    }
    
    public void replaceWholeText(String content)
    {
        replaceText(CanonicalLocation.ZERO, getText().length() * CanonicalLocation.ONE, content);
    }

    public void replaceText(@CanonicalLocation int startIncl, @CanonicalLocation int endExcl, String content)
    {
        replaceText(startIncl, endExcl, content, null);
    }

    @SuppressWarnings("units")
    public void replaceText(@CanonicalLocation int startIncl, @CanonicalLocation int endExcl, String content, @Nullable @CanonicalLocation Integer setCaretPos)
    {
        String newText = curContent.adjustedContent.substring(0, startIncl) + content + curContent.adjustedContent.substring(endExcl);
        
        final @RawInputLocation int newCaretPos;
        if (curCaretPosition < startIncl)
            newCaretPos = curCaretPosition;
        else if (curCaretPosition <= endExcl)
            newCaretPos = startIncl + content.length();
        else
            newCaretPos = curCaretPosition - (endExcl - startIncl) + content.length();
        this.curContent = lexer.process(newText, newCaretPos, this);
        if (setCaretPos != null)
            this.curCaretPosition = setCaretPos;
        else
            this.curCaretPosition = curContent.removedChars.map(newCaretPos);
        this.curAnchorPosition = curCaretPosition;
        Log.debug(">>>" + curContent.adjustedContent + " //" + curCaretPosition);
        for (FXPlatformRunnable contentListener : contentListeners)
        {
            contentListener.run();
        }
        notifyCaretPositionListeners(CaretMoveReason.TEXT_CHANGED);
    }
    
    public void forceSaveAsIfUnfocused()
    {
        this.curContent = lexer.process(getText(), null, this);
        @SuppressWarnings("units")
        @RawInputLocation int oldPos = getCaretPosition();
        this.curCaretPosition = curContent.removedChars.map(oldPos);
        this.curAnchorPosition = curCaretPosition;
        for (FXPlatformRunnable contentListener : contentListeners)
        {
            contentListener.run();
        }
        notifyCaretPositionListeners(CaretMoveReason.FORCED_SAVE);
    }

    public String getText()
    {
        return curContent.adjustedContent;
    }
    
    public List<Text> getDisplayText()
    {
        return curContent.display.toGUI();
    }

    public void addChangeListener(FXPlatformRunnable listener)
    {
        this.contentListeners.add(listener);
    }
    
    public LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> getLexerResult()
    {
        return curContent;
    }

    // How many right presses (positive) or left (negative) to
    // reach nearest end of given content?
    // Empty string is treated as the end
    public int _test_getCaretMoveDistance(String targetContent)
    {
        int targetStartIndex = targetContent.isEmpty() ? curContent.adjustedContent.length() : curContent.adjustedContent.indexOf(targetContent);
        if (!targetContent.isEmpty() && curContent.adjustedContent.indexOf(targetContent, targetStartIndex + 1) != -1)
            throw new RuntimeException("Content " + targetContent + " appears multiple times in editor");
        int targetEndIndex = targetStartIndex + targetContent.length();
        
        int caretIndex = Utility.<Integer>findFirstIndex(Utility.<CaretPos, Integer>mapList(curContent.caretPositions, p -> p.positionInternal), c -> c == curCaretPosition).orElseThrow(() -> new RuntimeException("Could not find caret position"));
        if (curCaretPosition < targetStartIndex)
        {
            int hops = 0;
            while (curContent.caretPositions.get(caretIndex + hops).positionInternal < targetStartIndex)
                hops += 1;
            return hops;
        }
        else if (curCaretPosition > targetEndIndex)
        {
            int hops = 0;
            while (curContent.caretPositions.get(caretIndex + hops).positionInternal > targetEndIndex)
                hops -= 1;
            return hops;
        }
        return 0;
    }

    public void addCaretPositionListener(CaretPositionListener listener)
    {
        caretPositionListeners.add(listener);
    }

    public ImmutableList<ErrorDetails> getErrors()
    {
        return curContent.errors;
    }

    @SuppressWarnings("units") // Because of toArray
    public @CanonicalLocation int[] getValidCaretPositions()
    {
        return Ints.toArray(Utility.mapList(curContent.caretPositions, p -> p.positionInternal));
    }

    // Note: this is not the caret position, but instead an index
    // into the getValidCaretPositions array.
    public int getCaretPosAsValidIndex()
    {
        for (int i = 0; i < curContent.caretPositions.size(); i++)
        {
            if (curCaretPosition == curContent.caretPositions.get(i).positionInternal)
                return i;
        }
        return 0;
    }
    
    public @CanonicalLocation int prevWordPosition(boolean canStaySame)
    {
        int index = Utility.findLastIndex(curContent.wordBoundaryCaretPositions, p -> canStaySame ? p <= curCaretPosition : p < curCaretPosition).orElse(0);
        return curContent.wordBoundaryCaretPositions.get(index);
    }

    public @CanonicalLocation int nextWordPosition()
    {
        int index = Utility.findFirstIndex(curContent.wordBoundaryCaretPositions, p -> p > curCaretPosition).orElse(curContent.wordBoundaryCaretPositions.size() - 1);
        return curContent.wordBoundaryCaretPositions.get(index);
    }

    public boolean suppressBracketMatch(int caretPosition)
    {
        return curContent.suppressBracketMatching.get(caretPosition);
    }
    
    public boolean areBracketsBalanced()
    {
        return curContent.bracketsAreBalanced;
    }

    public @DisplayLocation int getDisplayCaretPosition()
    {
        return curContent.mapContentToDisplay(getCaretPosition());
    }

    public @DisplayLocation int getDisplayAnchorPosition()
    {
        return curContent.mapContentToDisplay(getAnchorPosition());
    }

    public @CanonicalLocation int mapDisplayToContent(@DisplayLocation int clickedCaretPos, boolean biasEarlier)
    {
        return curContent.mapDisplayToContent(clickedCaretPos, biasEarlier);
    }
    
    public @DisplayLocation int mapContentToDisplay(@CanonicalLocation int contentIndex)
    {
        return curContent.mapContentToDisplay(contentIndex);
    }

    public StyledString getDisplay()
    {
        return curContent.display;
    }

    public ImmutableMap<DisplayType, Pair<StyledString, ImmutableList<TextQuickFix>>> getDisplayFor(@CanonicalLocation int newCaretPos, Node toRightOf)
    {
        return getLexerResult().infoAndPromptForPosition.apply(newCaretPos, toRightOf);
    }

    public void undo()
    {
        @Nullable Pair<String, @CanonicalLocation Integer> possible = undoManager.undo();
        if (possible != null)
        {
            replaceWholeText(possible.getFirst());
            positionCaret(possible.getSecond(), true);
        }
    }

    public void redo()
    {
        @Nullable Pair<String, @CanonicalLocation Integer> possible = undoManager.redo();
        if (possible != null)
        {
            replaceWholeText(possible.getFirst());
            positionCaret(possible.getSecond(), true);
        }
    }

    @Override
    public void insert(@Nullable @CanonicalLocation Integer start, String text)
    {
        @CanonicalLocation int caretPosition = getCaretPosition();
        if (start == null || start <= caretPosition)
            replaceText(start == null ? caretPosition : start, caretPosition, text);
    }
    
    public static enum CaretMoveReason
    {
        FORCED_SAVE, CARET_MOVED, FOCUSED, TEXT_CHANGED

    }
    
    public static interface CaretPositionListener
    {
        public void caretMoved(@CanonicalLocation int caretPos, CaretMoveReason caretMoveReason);
    }
}
