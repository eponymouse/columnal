module xyz.columnal.lexeditor
{
    exports xyz.columnal.gui.lexeditor;
    
    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.data;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.lexeditor.completion;
    requires xyz.columnal.types;
    requires xyz.columnal.expressions;
    requires xyz.columnal.utility.gui;
    requires xyz.columnal.parsers;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;

    requires com.google.common;
    requires java.desktop;
    requires java.xml;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    requires static org.checkerframework.checker.qual;
    requires org.controlsfx.controls;
}
