module xyz.columnal.identifiers
{
    exports xyz.columnal.id;

    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.parsers;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;

    requires com.google.common;
}
