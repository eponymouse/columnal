module xyz.columnal.stf
{
    exports xyz.columnal.gui.dtf;
    exports xyz.columnal.gui.grid;
    exports xyz.columnal.gui.stable;
    
    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.data;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.types;
    requires xyz.columnal.parsers;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    requires xyz.columnal.utility.gui;
    

    requires com.google.common;
    requires javafx.controls;
    requires javafx.graphics;
    requires wellbehavedfx;
    requires static org.checkerframework.checker.qual;
}
