module app
{
    exports records.gui;

    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires expressions;
    requires functions;
    requires guiutility;
    requires lexeditor;
    requires parsers;
    requires rinterop;
    requires stf;
    requires transformations;
    requires utility;
    
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
