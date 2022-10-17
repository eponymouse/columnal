module xyz.columnal.expressions
{
    exports xyz.columnal.transformations.expression;
    exports xyz.columnal.transformations.expression.explanation;
    exports xyz.columnal.transformations.expression.function;
    exports xyz.columnal.transformations.expression.type;
    exports xyz.columnal.transformations.expression.visitor;
    
    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.parsers;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;

    requires common;
    requires com.google.common;
    requires javafx.graphics;
    requires static org.checkerframework.checker.qual;
    requires org.antlr.antlr4.runtime;
    requires org.apache.commons.lang3;
}
