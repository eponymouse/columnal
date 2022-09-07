module lexeditor
{
    exports xyz.columnal.gui.lexeditor;
    exports xyz.columnal.gui.lexeditor.completion;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires expressions;
    requires xyz.columnal.utility.gui;
    requires parsers;
    requires xyz.columnal.utility;

    requires com.google.common;
    requires java.desktop;
    requires java.xml;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    //requires static org.checkerframework.checker;
}
