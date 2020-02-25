module data
{
    exports records.data;
    exports records.data.columntype;
    exports records.data.datatype;
    exports records.data.unit;
    exports records.jellytype;
    exports records.loadsave;
    exports records.typeExp;
    exports records.typeExp.units;

    requires anns;
    requires annsthreadchecker;
    requires parsers;
    requires utility;

    requires antlr4.runtime;
    requires com.google.common;
    requires common;
    requires javafx.graphics;
    requires org.checkerframework.checker;
    requires smtinterpol;
}
