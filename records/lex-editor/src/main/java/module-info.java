module lexeditor
{
    exports records.gui.lexeditor;
    exports records.gui.lexeditor.completion;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires expressions;
    requires guiutility;
    requires parsers;
    requires utility;

    requires com.google.common;
    requires java.desktop;
    requires java.xml;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    requires static org.checkerframework.checker;
}
