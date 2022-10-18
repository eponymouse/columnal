module xyz.columnal.lexeditor.completion {
    exports xyz.columnal.gui.lexeditor.completion;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;

    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    requires xyz.columnal.utility.gui;
    
    requires java.xml;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
}