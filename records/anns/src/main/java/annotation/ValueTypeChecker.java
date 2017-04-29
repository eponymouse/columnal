package annotation;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.qual.ValueBottom;
import annotation.userindex.UserIndexChecker;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree.Kind;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.qual.PolyAll;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.javacutil.AnnotationUtils;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;
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
                    // Need to let literals be @Value for booleans and empty string:
                    protected TreeAnnotator createTreeAnnotator() {
                        return new ListTreeAnnotator(new TreeAnnotator[]{super.createTreeAnnotator(), new ValueTypeTreeAnnotator(this, elements)});
                    }

                    class ValueTypeTreeAnnotator extends TreeAnnotator {
                        private final AnnotationMirror VALUE;

                        public ValueTypeTreeAnnotator(BaseAnnotatedTypeFactory atypeFactory, Elements elements)
                        {
                            super(atypeFactory);
                            this.VALUE = AnnotationUtils.fromClass(elements, Value.class);
                        }

                        public Void visitLiteral(LiteralTree tree, AnnotatedTypeMirror type)
                        {

                            if(!type.isAnnotatedInHierarchy(this.VALUE))
                            {
                                // Empty string is automatically @Value
                                if (tree.getKind() == Kind.STRING_LITERAL && tree.getValue().equals(""))
                                {
                                    type.addAnnotation(this.VALUE);
                                }
                                // So are boolean literals:
                                if(tree.getKind() == Kind.BOOLEAN_LITERAL)
                                {
                                    type.addAnnotation(this.VALUE);
                                }
                            }

                            return (Void)super.visitLiteral(tree, type);
                        }
                    }


                    // This follow part of the body of the class is only needed to work around some kind of
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
