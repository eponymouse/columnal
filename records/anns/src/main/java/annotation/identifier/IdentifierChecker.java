package annotation.identifier;

import annotation.help.qual.HelpKey;
import annotation.identifier.qual.ExpressionIdentifier;
import annotation.identifier.qual.IdentifierBottom;
import annotation.identifier.qual.UnitIdentifier;
import annotation.identifier.qual.UnknownIfIdentifier;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree.Kind;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.javacutil.AnnotationBuilder;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by neil on 29/04/2017.
 */
public class IdentifierChecker extends BaseTypeChecker
{
    @Override
    protected BaseTypeVisitor<?> createSourceVisitor()
    {
        return new BaseTypeVisitor(this)
        {
            @Override
            protected GenericAnnotatedTypeFactory<?, ?, ?, ?> createTypeFactory()
            {
                return new BaseAnnotatedTypeFactory(IdentifierChecker.this, false) {

                    // Need to check string literals:
                    protected TreeAnnotator createTreeAnnotator() {
                        return new ListTreeAnnotator(new TreeAnnotator[]{super.createTreeAnnotator(), new StringLiteralAnnotator(this, elements)});
                    }

                    class StringLiteralAnnotator extends TreeAnnotator {
                        private final AnnotationMirror EXPRESSION_IDENTIFIER;

                        public StringLiteralAnnotator(BaseAnnotatedTypeFactory atypeFactory, Elements elements)
                        {
                            super(atypeFactory);
                            this.EXPRESSION_IDENTIFIER = AnnotationBuilder.fromClass(elements, ExpressionIdentifier.class);
                        }

                        public Void visitLiteral(LiteralTree tree, AnnotatedTypeMirror type)
                        {
                            if(!type.isAnnotatedInHierarchy(this.EXPRESSION_IDENTIFIER))
                            {
                                if (tree.getKind() == Kind.STRING_LITERAL)
                                {
                                    String value = tree.getValue().toString();
                                    if (value.matches("^[a-zA-Z]+$"))
                                        type.addAnnotation(this.EXPRESSION_IDENTIFIER);
                                }
                            }

                            return (Void)super.visitLiteral(tree, type);
                        }
                    }
                    
                    
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
                                UnknownIfIdentifier.class,
                                ExpressionIdentifier.class,
                                UnitIdentifier.class,
                                IdentifierBottom.class
                        ));
                    }
                };
            }
        };
    }
}
