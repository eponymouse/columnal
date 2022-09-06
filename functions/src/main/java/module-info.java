module functions
{
    exports records.transformations.function;
    exports records.transformations.function.comparison;
    exports records.transformations.function.conversion;
    exports records.transformations.function.core;
    exports records.transformations.function.list;
    exports records.transformations.function.optional;
    exports records.transformations.function.text;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires expressions;
    requires parsers;
    requires xyz.columnal.utility;

    requires big.math;
    requires com.google.common;
    requires commons.lang3;
    //requires static org.checkerframework.checker;
}
