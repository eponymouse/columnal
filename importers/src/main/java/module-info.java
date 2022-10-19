module xyz.columnal.importers {
    exports xyz.columnal.importers;
    exports xyz.columnal.importers.gui;
    exports xyz.columnal.importers.manager;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;

    requires xyz.columnal.data;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.parsers;
    requires xyz.columnal.rinterop;
    requires xyz.columnal.stf;
    requires xyz.columnal.table.gui;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.gui;
    requires xyz.columnal.utility.functional;
    
    
    requires com.google.common;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;
    requires org.jsoup;
}
