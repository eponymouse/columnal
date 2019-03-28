package records.gui.lexeditor;

import records.gui.lexeditor.TopLevelEditor.Focus;
import utility.FXPlatformConsumer;

import java.util.ArrayList;

public class EditorContent
{
    private String curContent;
    private int[] validCaretPositions; // Should always be at least one
    private int curCaretPosition;
    private final Lexer lexer;
    private final ArrayList<FXPlatformConsumer<Integer>> caretPositionListeners = new ArrayList<>();
    
    public EditorContent(String originalContent, Lexer lexer)
    {
        this.lexer = lexer;
        curContent = originalContent;
        this.lexer.update(curContent);
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
}
