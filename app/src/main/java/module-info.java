module xyz.columnal.app
{
    exports xyz.columnal.gui;

    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.data;
    requires xyz.columnal.expressions;
    requires xyz.columnal.functions;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.gui;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.lexeditor;
    requires xyz.columnal.parsers;
    requires xyz.columnal.rinterop;
    requires xyz.columnal.stf;
    requires xyz.columnal.transformations;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    
    requires java.desktop;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    
    requires org.controlsfx.controls;
    requires com.google.common;
    requires static org.checkerframework.checker.qual;
    requires org.jsoup;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires org.antlr.antlr4.runtime;
    requires org.apache.commons.io;
    requires org.apache.commons.text;
    requires common;
}
