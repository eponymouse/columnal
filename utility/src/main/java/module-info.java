module xyz.columnal.utility
{
    exports xyz.columnal.log;
    exports xyz.columnal.utility;
    exports xyz.columnal.error.parse;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;
    requires xyz.columnal.parsers;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;

    requires com.google.common;
    requires javafx.base;
    requires javafx.graphics;
    requires org.apache.logging.log4j;
    requires org.antlr.antlr4.runtime;
    requires org.apache.commons.io;
    requires java.string.similarity;
    requires common;
    requires org.apache.commons.lang3;


}
