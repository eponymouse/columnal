package records.gui.lexeditor;

import records.gui.lexeditor.Lexer.CaretPosMapper;
import records.gui.lexeditor.TopLevelEditor.Focus;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.ArrayList;

public class EditorContent
{
    private String curContent;
    private int[] validCaretPositions; // Should always be at least one
    private int curCaretPosition;
    private final Lexer lexer;
    private final ArrayList<FXPlatformConsumer<Integer>> caretPositionListeners = new ArrayList<>();
    private final ArrayList<FXPlatformRunnable> contentListeners = new ArrayList<>();
    
    public EditorContent(String originalContent, Lexer lexer)
    {
        this.lexer = lexer;
        this.curContent = this.lexer.process(originalContent).getFirst();
        this.validCaretPositions = this.lexer.getCaretPositions();
        this.curCaretPosition = validCaretPositions.length > 0 ? validCaretPositions[0] : 0;
    }

    public void positionCaret(Focus side)
    {
        if (side == Focus.LEFT && validCaretPositions.length > 0)
            curCaretPosition = validCaretPositions[0];
        else if (side == Focus.RIGHT && validCaretPositions.length > 0)
            curCaretPosition = validCaretPositions[validCaretPositions.length - 1];

        for (FXPlatformConsumer<Integer> caretPositionListener : caretPositionListeners)
        {
            caretPositionListener.consume(curCaretPosition);
        }
    }
    
    public int getCaretPosition()
    {
        return curCaretPosition;
    }
    
    public void replaceText(int startIncl, int endExcl, String content)
    {
        String newText = curContent.substring(0, startIncl) + content + curContent.substring(endExcl);
        int newCaretPos = curCaretPosition < startIncl ? curCaretPosition : (curCaretPosition <= endExcl ? startIncl + content.length() : (curCaretPosition - (endExcl - startIncl) + content.length()));  
        Pair<String, CaretPosMapper> processed = lexer.process(newText);
        this.curContent = processed.getFirst();
        this.curCaretPosition = processed.getSecond().mapCaretPos(newCaretPos);
        this.validCaretPositions = lexer.getCaretPositions();
        for (FXPlatformRunnable contentListener : contentListeners)
        {
            contentListener.run();
        }
    }

    public String getText()
    {
        return curContent;
    }

    public void addChangeListener(FXPlatformRunnable listener)
    {
        this.contentListeners.add(listener);
    }
}
