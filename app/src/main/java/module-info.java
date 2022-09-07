module app
{
    exports xyz.columnal.gui;

    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires expressions;
    requires functions;
    requires xyz.columnal.utility.gui;
    requires lexeditor;
    requires parsers;
    requires rinterop;
    requires stf;
    requires transformations;
    requires xyz.columnal.utility;
    
    requires java.desktop;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.web;
    
    requires controlsfx;
    requires com.google.common;
    //requires static org.checkerframework.checker;
    requires org.jsoup;
    requires poi;
    requires poi.ooxml;
}
