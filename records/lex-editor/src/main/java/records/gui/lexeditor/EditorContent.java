package records.gui.lexeditor;

import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import javafx.scene.text.Text;
import log.Log;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import records.gui.lexeditor.Lexer.LexerResult;
import records.gui.lexeditor.TopLevelEditor.Focus;
import styled.StyledShowable;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

public final class EditorContent<EXPRESSION extends StyledShowable, CODE_COMPLETION_CONTEXT extends CodeCompletionContext>
{
    private LexerResult<EXPRESSION, CODE_COMPLETION_CONTEXT> curContent;
    private @SourceLocation int curCaretPosition;
    private @SourceLocation int curAnchorPosition;
    private final Lexer<EXPRESSION, CODE_COMPLETION_CONTEXT> lexer;
    private final ArrayList<FXPlatformConsumer<@SourceLocation Integer>> caretPositionListeners = new ArrayList<>();
    private final ArrayList<FXPlatformRunnable> contentListeners = new ArrayList<>();
    
    @SuppressWarnings("units")
    public EditorContent(String originalContent, Lexer<EXPRESSION, CODE_COMPLETION_CONTEXT> lexer)
    {
        this.lexer = lexer;
        this.curContent = this.lexer.process(originalContent, 0);
        this.curCaretPosition = curContent.caretPositions.length > 0 ? curContent.caretPositions[0] : 0;
        this.curAnchorPosition = curCaretPosition;
    }

    public void positionCaret(Focus side)
    {
        if (side == Focus.LEFT && curContent.caretPositions.length > 0)
            positionCaret(curContent.caretPositions[0], true);
        else if (side == Focus.RIGHT && curContent.caretPositions.length > 0)
            positionCaret(curContent.caretPositions[curContent.caretPositions.length - 1], true);
    }
    
    public void positionCaret(@SourceLocation int pos, boolean alsoSetAnchor)
    {
        curCaretPosition = pos;
        if (alsoSetAnchor)
            curAnchorPosition = pos;
        for (FXPlatformConsumer<@SourceLocation Integer> caretPositionListener : caretPositionListeners)
        {
            caretPositionListener.consume(curCaretPosition);
        }
    }
    
    public @SourceLocation int getAnchorPosition()
    {
        return curAnchorPosition;
    }

    public @SourceLocation int getCaretPosition()
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
        @SourceLocation int newCaretPos = curCaretPosition < startIncl ? curCaretPosition : (curCaretPosition <= endExcl ? startIncl + content.length() : (curCaretPosition - (endExcl - startIncl) + content.length()));  
        this.curContent = lexer.process(newText, newCaretPos);
        this.curCaretPosition = curContent.mapperToAdjusted.mapCaretPos(newCaretPos);
        this.curAnchorPosition = curCaretPosition;
        Log.debug(">>>" + curContent.adjustedContent + " //" + curCaretPosition);
        for (FXPlatformRunnable contentListener : contentListeners)
        {
            contentListener.run();
        }
        for (FXPlatformConsumer<@SourceLocation Integer> caretPositionListener : caretPositionListeners)
        {
            caretPositionListener.consume(curCaretPosition);
        }
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
    public int _test_getCaretMoveDistance(String targetContent)
    {
        int targetStartIndex = curContent.adjustedContent.indexOf(targetContent);
        if (curContent.adjustedContent.indexOf(targetContent, targetStartIndex + 1) != -1)
            throw new RuntimeException("Content " + targetContent + " appears multiple times in editor");
        int targetEndIndex = targetStartIndex + targetContent.length();
        
        int caretIndex = Utility.findFirstIndex(Ints.asList(curContent.caretPositions), c -> c.intValue() == curCaretPosition).orElseThrow(() -> new RuntimeException("Could not find caret position"));
        if (curCaretPosition < targetStartIndex)
        {
            int hops = 0;
            while (curContent.caretPositions[caretIndex + hops] < targetStartIndex)
                hops += 1;
            return hops;
        }
        else if (curCaretPosition > targetEndIndex)
        {
            int hops = 0;
            while (curContent.caretPositions[caretIndex + hops] > targetEndIndex)
                hops -= 1;
            return hops;
        }
        return 0;
    }

    public void addCaretPositionListener(FXPlatformConsumer<@SourceLocation Integer> listener)
    {
        caretPositionListeners.add(listener);
    }

    public ImmutableList<ErrorDetails> getErrors()
    {
        return curContent.errors;
    }

    public @SourceLocation int[] getValidCaretPositions()
    {
        return curContent.caretPositions;
    }

    // Note: this is not the caret position, but instead an index
    // into the getValidCaretPositions array.
    public int getCaretPosAsValidIndex()
    {
        for (int i = 0; i < curContent.caretPositions.length; i++)
        {
            if (curCaretPosition == curContent.caretPositions[i])
                return i;
        }
        return 0;
    }

    public boolean suppressBracketMatch(int caretPosition)
    {
        return curContent.suppressBracketMatching.get(caretPosition);
    }
    
    public boolean areBracketsBalanced()
    {
        return curContent.bracketsAreBalanced;
    }

    public int getDisplayCaretPosition()
    {
        return curContent.mapContentToDisplay.mapCaretPos(getCaretPosition());
    }

    public int getDisplayAnchorPosition()
    {
        return curContent.mapContentToDisplay.mapCaretPos(getAnchorPosition());
    }
}
