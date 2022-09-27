module xyz.columnal.utility.gui
{
    exports xyz.columnal.utility.gui;
    // Need to be opens for JNA access on Windows:
    opens xyz.columnal.utility.gui;

    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.parsers;
    requires xyz.columnal.utility;

    requires com.google.common;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires controlsfx;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires thumbnailator;
    requires wellbehavedfx;
    requires static org.checkerframework.checker.qual;
    requires commons.lang3;
    requires org.antlr.antlr4.runtime;
}
