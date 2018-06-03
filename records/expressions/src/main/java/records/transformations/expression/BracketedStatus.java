package records.transformations.expression;

public enum BracketedStatus
{
    /** Direct round brackets, i.e. if there's only commas, this can be a tuple expression */
    DIRECT_ROUND_BRACKETED,
    /** Direct square brackets, i.e. this has to be an array expression */
    DIRECT_SQUARE_BRACKETED,
    /** Top level in an expression, i.e. you don't need brackets around operators, but do around a tuple */
    TOP_LEVEL, 
    /* Normal state: the others above don't apply */
    MISC;
}
