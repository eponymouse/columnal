package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import annotation.units.RawInputLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import javafx.scene.text.Text;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.Lexer.LexerResult;
import records.gui.lexeditor.Lexer.LexerResult.CaretPos;
import records.gui.lexeditor.TopLevelEditor.Focus;
import styled.StyledShowable;
import styled.StyledString;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class EditorContent<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    private LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> curContent;
    private @CanonicalLocation int curCaretPosition;
    private @CanonicalLocation int curAnchorPosition;
    private final Lexer<EXPRESSION, CODE_COMPLETION_CONTEXT> lexer;
    private final ArrayList<FXPlatformConsumer<@CanonicalLocation Integer>> caretPositionListeners = new ArrayList<>();
    private final ArrayList<FXPlatformRunnable> contentListeners = new ArrayList<>();
    
    @SuppressWarnings("units")
    public EditorContent(String originalContent, Lexer<EXPRESSION, CODE_COMPLETION_CONTEXT> lexer)
    {
        this.lexer = lexer;
        this.curContent = this.lexer.process(originalContent, 0);
        this.curCaretPosition = curContent.caretPositions.size() > 0 ? curContent.caretPositions.get(0).positionInternal : 0;
        this.curAnchorPosition = curCaretPosition;
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
        notifyCaretPositionListeners();
    }

    void notifyCaretPositionListeners()
    {
        for (FXPlatformConsumer<@CanonicalLocation Integer> caretPositionListener : caretPositionListeners)
        {
            caretPositionListener.consume(curCaretPosition);
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
        replaceText(Math.min(curCaretPosition, curAnchorPosition), Math.max(curCaretPosition, curAnchorPosition), content);
        curAnchorPosition = curCaretPosition;
    }
    
    public void replaceText(int startIncl, int endExcl, String content)
    {
        String newText = curContent.adjustedContent.substring(0, startIncl) + content + curContent.adjustedContent.substring(endExcl);
        @SuppressWarnings("units")
        @RawInputLocation int newCaretPos = curCaretPosition < startIncl ? curCaretPosition : (curCaretPosition <= endExcl ? startIncl + content.length() : (curCaretPosition - (endExcl - startIncl) + content.length()));  
        this.curContent = lexer.process(newText, newCaretPos);
        this.curCaretPosition = curContent.removedChars.map(newCaretPos);
        this.curAnchorPosition = curCaretPosition;
        Log.debug(">>>" + curContent.adjustedContent + " //" + curCaretPosition);
        for (FXPlatformRunnable contentListener : contentListeners)
        {
            contentListener.run();
        }
        notifyCaretPositionListeners();
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

    public void addCaretPositionListener(FXPlatformConsumer<@CanonicalLocation Integer> listener)
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
        int index = Utility.findLastIndex(curContent.wordBoundaryCaretPositions, p -> canStaySame ? p.positionInternal <= curCaretPosition : p.positionInternal < curCaretPosition).orElse(0);
        return curContent.wordBoundaryCaretPositions.get(index).positionInternal;
    }

    public @CanonicalLocation int nextWordPosition()
    {
        int index = Utility.findFirstIndex(curContent.wordBoundaryCaretPositions, p -> p.positionInternal > curCaretPosition).orElse(curContent.wordBoundaryCaretPositions.size() - 1);
        return curContent.wordBoundaryCaretPositions.get(index).positionInternal;
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

    public @Nullable StyledString getEntryPromptFor(@CanonicalLocation int newCaretPos)
    {
        StyledString prompt = Utility.filterOutNulls(curContent.autoCompleteDetails.stream().filter(acd -> acd.location.touches(newCaretPos)).<@Nullable StyledString>map(acd -> acd.codeCompletionContext.getEntryPrompt())).collect(StyledString.joining("\n"));
        return prompt.toPlain().isEmpty() ? null : prompt;
    }
}
