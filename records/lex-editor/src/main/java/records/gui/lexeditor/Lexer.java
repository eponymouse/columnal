package records.gui.lexeditor;

import utility.Pair;

public interface Lexer
{
    public interface CaretPosMapper
    {
        public int mapCaretPos(int pos);
    }
    
    // Takes latest content, lexes it, returns resulting String and a mapper for positions in the parameter string
    public Pair<String, CaretPosMapper> process(String content);
    
    public int[] getCaretPositions();
}
