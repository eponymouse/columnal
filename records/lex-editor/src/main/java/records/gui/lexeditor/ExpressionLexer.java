package records.gui.lexeditor;

import utility.Pair;

public class ExpressionLexer implements Lexer
{
    @Override
    public Pair<String, CaretPosMapper> process(String content)
    {
        return new Pair<>(content, i -> i);
    }

    @Override
    public int[] getCaretPositions()
    {
        return new int[] {0}; // TODO
    }
}
