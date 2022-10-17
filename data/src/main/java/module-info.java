module xyz.columnal.data
{
    exports xyz.columnal.data;
    exports xyz.columnal.data.columntype;

    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.parsers;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    

    requires org.antlr.antlr4.runtime;
    requires com.google.common;
    //requires common;
    requires javafx.graphics;
    requires one.util.streamex;
    requires static org.checkerframework.checker.qual;
}
