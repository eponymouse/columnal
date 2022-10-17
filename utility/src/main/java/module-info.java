module xyz.columnal.utility
{
    exports xyz.columnal.log;
    exports xyz.columnal.error;
    exports xyz.columnal.styled;
    exports xyz.columnal.utility;
    exports xyz.columnal.utility.function.simulation;
    exports xyz.columnal.utility.function.fx;
    exports xyz.columnal.utility.function;
    exports xyz.columnal.error.parse;
    exports xyz.columnal.error.style;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;
    requires xyz.columnal.parsers;

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
