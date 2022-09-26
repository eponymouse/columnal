module xyz.columnal.app
{
    exports xyz.columnal.gui;

    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.data;
    requires xyz.columnal.expressions;
    requires xyz.columnal.functions;
    requires xyz.columnal.utility.gui;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.lexeditor;
    requires xyz.columnal.parsers;
    requires xyz.columnal.rinterop;
    requires xyz.columnal.stf;
    requires xyz.columnal.transformations;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    
    requires java.desktop;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    
    requires controlsfx;
    requires com.google.common;
    requires static org.checkerframework.checker.qual;
    requires org.jsoup;
    requires poi;
    requires poi.ooxml;
    requires org.antlr.antlr4.runtime;
    requires commons.io;
    requires common;
}
