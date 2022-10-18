module xyz.columnal.types
{
    exports xyz.columnal.data.datatype;
    exports xyz.columnal.data.unit;
    exports xyz.columnal.jellytype;
    exports xyz.columnal.loadsave;
    exports xyz.columnal.typeExp;
    exports xyz.columnal.typeExp.units;

    requires static anns;
    requires static annsthreadchecker;
    requires static org.checkerframework.checker.qual;
    requires xyz.columnal.parsers;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    requires xyz.columnal.identifiers;

    requires org.antlr.antlr4.runtime;
    requires com.google.common;
    requires one.util.streamex;
    requires org.apache.commons.io;
    requires common;
}
