module stf
{
    exports records.gui.dtf;
    exports records.gui.grid;
    exports records.gui.stable;
    
    requires anns;
    requires annsthreadchecker;
    requires data;
    requires guiutility;
    requires parsers;
    requires utility;

    requires com.google.common;
    requires javafx.controls;
    requires javafx.graphics;
    requires org.checkerframework.checker;
    requires wellbehavedfx;
}
