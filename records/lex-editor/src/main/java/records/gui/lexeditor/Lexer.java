package records.gui.lexeditor;

public interface Lexer
{
    public void update(String content);
    
    public int[] getCaretPositions();
}
