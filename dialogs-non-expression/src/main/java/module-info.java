module xyz.columnal.gui.dialog {
    exports xyz.columnal.gui.dialog;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;

    requires xyz.columnal.data;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.parsers;
    requires xyz.columnal.transformations;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    requires xyz.columnal.utility.gui;

    requires com.google.common;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    requires org.controlsfx.controls;
}
