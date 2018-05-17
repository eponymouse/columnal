package annotation.funcdoc;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.funcdoc.qual.FuncDocKeyBottom;
import annotation.funcdoc.qual.UnknownIfFuncDoc;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree.Kind;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.ParsingException;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.javacutil.AnnotationBuilder;

import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * We have two keys;
 * @FuncDocKey, which checks that a given item is a namespace/function-name key,
 *   and that the key only occurs once in the xml files.
 */
@SupportedOptions({"funcdocfiles"})
public class FuncDocChecker extends BaseTypeChecker
{
    @Override
    protected BaseTypeVisitor<?> createSourceVisitor()
    {
        return new BaseTypeVisitor(this)
        {
            @Override
            protected GenericAnnotatedTypeFactory<?, ?, ?, ?> createTypeFactory()
            {
                // Since we do a lot of checks on Strings, best to cache all available keys
                // first and check against that, rather than reload files every time:

                return new BaseAnnotatedTypeFactory(FuncDocChecker.this, false) {
                    // Namespace, function name
                    private final Set<List<String>> individualFunctionKeys = new HashSet<>();
                    private final List<String> errors = new ArrayList<>();

                    {
                        if (this.checker.hasOption("funcdocfiles"))
                        {
                            String[] allFiles = this.checker.getOption("funcdocfiles").split(";");
                            Builder builder = new Builder();
                            for (String fileName : allFiles)
                            {
                                try
                                {
                                    File file = new File(fileName);
                                    Document doc = builder.build(file);
                                    Element root = doc.getRootElement();
                                    if (!root.getLocalName().equals("functionDocumentation"))
                                    {
                                        errors.add("Unknown root element: " + root.getLocalName());
                                    }
                                    String namespace = root.getAttributeValue("namespace");
                                    if (namespace == null)
                                    {
                                        errors.add("Missing namespace for " + file);
                                        continue;
                                    }
                                    
                                    nu.xom.Elements functions = root.getChildElements("function");
                                    processFunctions(fileName, namespace, functions);
                                }
                                catch (IOException | ParsingException e)
                                {
                                    errors.add("Error loading " + fileName + ": " + e.getLocalizedMessage());
                                }
                            }
                        }
                        else
                        {
                            errors.add("No funcdocfiles parameter specified");
                        }
                    }

                    private void processFunctions(String fileName, String namespace, nu.xom.Elements functions)
                    {
                        for (int i = 0; i < functions.size(); i++)
                        {
                            String funcName = functions.get(i).getAttributeValue("name");
                            if (funcName == null)
                            {
                                errors.add("No name found for function item " + i + " in file " + fileName);
                            }
                            else
                            {
                                if (!individualFunctionKeys.add(Arrays.asList(namespace, funcName)))
                                    errors.add("Duplicate key found for " + namespace + ":" + funcName);
                            }
                        }
                    }

                    // Need to look up string literals:
                    protected TreeAnnotator createTreeAnnotator() {
                        return new ListTreeAnnotator(new TreeAnnotator[]{super.createTreeAnnotator(), new ValueTypeTreeAnnotator(this, elements)});
                    }

                    class ValueTypeTreeAnnotator extends TreeAnnotator {
                        private final AnnotationMirror FUNCDOC_KEY;

                        public ValueTypeTreeAnnotator(BaseAnnotatedTypeFactory atypeFactory, Elements elements)
                        {
                            super(atypeFactory);
                            this.FUNCDOC_KEY = AnnotationBuilder.fromClass(elements, FuncDocKey.class);
                        }

                        public Void visitLiteral(LiteralTree tree, AnnotatedTypeMirror type)
                        {
                            if (!errors.isEmpty())
                            {
                                for (String error : errors)
                                {
                                    trees.printMessage(javax.tools.Diagnostic.Kind.ERROR, error, tree, currentRoot);
                                }
                                // Only issue the errors once:
                                errors.clear();
                            }

                            if (!type.isAnnotatedInHierarchy(this.FUNCDOC_KEY))
                            {
                                if (tree.getKind() == Kind.STRING_LITERAL)
                                {
                                    String value = tree.getValue().toString();
                                    if (individualFunctionKeys.contains(Arrays.asList(value.split(":"))))
                                        type.addAnnotation(this.FUNCDOC_KEY);
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
                            UnknownIfFuncDoc.class,
                            FuncDocKey.class,
                            FuncDocKeyBottom.class
                        ));
                    }
                };
            }
        };
    }
}
