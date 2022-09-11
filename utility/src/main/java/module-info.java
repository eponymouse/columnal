module xyz.columnal.utility
{
    exports xyz.columnal.log;
    exports xyz.columnal.error;
    exports xyz.columnal.styled;
    exports xyz.columnal.utility;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;
    requires xyz.columnal.parsers;

    requires com.google.common;
    requires javafx.base;
    requires javafx.graphics;
    requires org.apache.logging.log4j;
    requires antlr4.runtime;
    requires commons.io;
    requires java.string.similarity;
    requires common;
    requires commons.lang3;


}
