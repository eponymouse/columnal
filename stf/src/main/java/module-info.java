module stf
{
    exports xyz.columnal.gui.dtf;
    exports xyz.columnal.gui.grid;
    exports xyz.columnal.gui.stable;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires xyz.columnal.utility.gui;
    requires parsers;
    requires xyz.columnal.utility;

    requires com.google.common;
    requires javafx.controls;
    requires javafx.graphics;
    //requires static org.checkerframework.checker;
    requires wellbehavedfx;
}
