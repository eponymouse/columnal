module anns {
    exports annotation.qual;
    exports annotation.funcdoc.qual;
    exports annotation.help.qual;
    exports annotation.identifier.qual;
    exports annotation.recorded.qual;
    exports annotation.units;
    exports annotation.userindex.qual;

    requires jdk.compiler;
    requires org.checkerframework.checker.qual;
}
