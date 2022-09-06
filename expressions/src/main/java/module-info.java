module expressions
{
    exports records.transformations.expression;
    exports records.transformations.expression.explanation;
    exports records.transformations.expression.function;
    exports records.transformations.expression.type;
    exports records.transformations.expression.visitor;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires parsers;
    requires utility;

    //requires common;
    requires com.google.common;
    requires javafx.graphics;
    //requires static org.checkerframework.checker;
}
