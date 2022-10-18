module xyz.columnal.functions
{
    exports xyz.columnal.transformations.function;
    exports xyz.columnal.transformations.function.comparison;
    exports xyz.columnal.transformations.function.conversion;
    exports xyz.columnal.transformations.function.core;
    exports xyz.columnal.transformations.function.list;
    exports xyz.columnal.transformations.function.optional;
    exports xyz.columnal.transformations.function.text;
    
    requires static anns;
    requires static annsthreadchecker;
    requires xyz.columnal.expressions;
    requires xyz.columnal.parsers;
    requires xyz.columnal.types;
    requires xyz.columnal.utility;
    requires xyz.columnal.utility.adt;
    requires xyz.columnal.utility.error;
    requires xyz.columnal.utility.functional;

    requires ch.obermuhlner.math.big;
    requires com.google.common;
    requires org.apache.commons.lang3;
    requires static org.checkerframework.checker.qual;
}
