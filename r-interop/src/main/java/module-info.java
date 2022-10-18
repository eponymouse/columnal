module xyz.columnal.rinterop
{
    exports xyz.columnal.rinterop;
    
    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.data;
    requires xyz.columnal.identifiers;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;
    
    requires com.google.common;
    requires org.apache.commons.io;
    requires org.apache.commons.lang3;
    requires static org.checkerframework.checker.qual;
}
