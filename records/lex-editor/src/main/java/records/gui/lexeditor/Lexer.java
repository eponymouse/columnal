package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import styled.StyledShowable;
import utility.Pair;

public interface Lexer<EXPRESSION extends StyledShowable>
{
    @Recorded EXPRESSION getSaved();

    public interface CaretPosMapper
    {
        public int mapCaretPos(int pos);
    }
    
    // Takes latest content, lexes it, returns resulting String and a mapper for positions in the parameter string
    public Pair<String, CaretPosMapper> process(String content);
    
    public int[] getCaretPositions();
}
