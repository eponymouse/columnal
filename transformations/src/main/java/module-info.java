module xyz.columnal.transformations
{
    exports xyz.columnal.error.expressions;
    exports xyz.columnal.transformations;

    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.data;
    requires xyz.columnal.expressions;
    requires xyz.columnal.functions;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.utility.gui;
    requires xyz.columnal.parsers;
    requires xyz.columnal.rinterop;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    
    requires com.google.common;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires static org.checkerframework.checker.qual;
    requires org.antlr.antlr4.runtime;
}
