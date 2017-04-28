package annotation;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.qual.ValueBottom;
import annotation.userindex.UserIndexChecker;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.qual.PolyAll;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by neil on 11/01/2017.
 */
public class ValueTypeChecker extends BaseTypeChecker
{
    /*
    @Override
    public void initChecker()
    {
        try
        {
            System.err.println("Calling parent initChecker");
            super.initChecker();
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            throw t;
        }
    }
    */

    @Override
    protected BaseTypeVisitor<?> createSourceVisitor()
    {
        return new BaseTypeVisitor(this)
        {
            @Override
            protected GenericAnnotatedTypeFactory<?, ?, ?, ?> createTypeFactory()
            {
                return new BaseAnnotatedTypeFactory(ValueTypeChecker.this, false) {
                    // This body of the class is only needed to work around some kind of
                    // bug in the import scanning when the class files are available in a directory
                    // rather than a JAR, which is true since we moved the annotations to a Maven module:
                    {
                        postInit();
                    }
                    @Override
                    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers()
                    {
                        return new HashSet<>(Arrays.asList(
                            UnknownIfValue.class,
                            Value.class,
                            ValueBottom.class
                        ));
                    }
                };
            }
        };
    }
}
