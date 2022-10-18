module xyz.columnal.exporters {
    exports xyz.columnal.exporters;
    exports xyz.columnal.exporters.manager;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;
    
    requires xyz.columnal.data;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.rinterop;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    requires xyz.columnal.utility.gui;
    
    requires com.google.common;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires org.apache.commons.text;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
}
