module data
{
    exports xyz.columnal.data;
    exports xyz.columnal.data.columntype;
    exports xyz.columnal.data.datatype;
    exports xyz.columnal.data.unit;
    exports xyz.columnal.jellytype;
    exports xyz.columnal.loadsave;
    exports xyz.columnal.typeExp;
    exports xyz.columnal.typeExp.units;

    requires static anns;
    requires static annsthreadchecker;
    requires parsers;
    requires xyz.columnal.utility;

    requires antlr4.runtime;
    requires com.google.common;
    //requires common;
    requires javafx.graphics;
    requires one.util.streamex;
    //requires static org.checkerframework.checker;
}