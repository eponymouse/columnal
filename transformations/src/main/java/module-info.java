module transformations
{
    exports records.errors;
    exports records.transformations;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires expressions;
    requires functions;
    requires xyz.columnal.utility.gui;
    requires parsers;
    requires rinterop;
    requires xyz.columnal.utility;
    
    requires com.google.common;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    //requires static org.checkerframework.checker;
}
