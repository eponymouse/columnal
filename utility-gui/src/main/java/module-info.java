module xyz.columnal.utility.gui
{
    exports xyz.columnal.utility.gui;
    // Need to be opens for JNA access on Windows:
    opens xyz.columnal.utility.gui;

    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.parsers;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;

    requires com.google.common;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.controlsfx.controls;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    requires thumbnailator;
    requires wellbehavedfx;
    requires static org.checkerframework.checker.qual;
    requires org.apache.commons.lang3;
    requires org.antlr.antlr4.runtime;
}
