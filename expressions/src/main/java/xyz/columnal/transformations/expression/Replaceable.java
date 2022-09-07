package xyz.columnal.transformations.expression;

public interface Replaceable<EXPRESSION>
{
    // Important: use reference comparison, not .equals().  And check against yourself as well as sub-expressions
    public EXPRESSION replaceSubExpression(EXPRESSION toReplace, EXPRESSION replaceWith);
}
