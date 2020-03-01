module guiutility
{
    exports utility.gui;

    requires static anns;
    requires static annsthreadchecker;
    requires parsers;
    requires utility;

    requires com.google.common;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires controlsfx;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires static org.checkerframework.checker;
    requires thumbnailator;
    requires wellbehavedfx;
}
