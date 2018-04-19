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
import org.checkerframework.com.google.common.collect.ImmutableList;
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
 * Created by neil on 29/04/2017.
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
                    private final Set<ImmutableList<String>> allKeys = new HashSet<>();
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
                                    String rootId = root.getAttributeValue("namespace");
                                    if (rootId == null)
                                    {
                                        errors.add("Missing namespace for " + file);
                                        continue;
                                    }
                                    
                                    nu.xom.Elements groups = root.getChildElements("functionGroup");
                                    for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++)
                                    {
                                        Element group = groups.get(groupIndex);
                                        String groupId = group.getAttributeValue("id");
                                        if (groupId == null)
                                        {
                                            errors.add("Missing group id for group " + groupIndex + " in " + file);
                                            continue;
                                        }
                                        nu.xom.Elements functions = group.getChildElements("function");
                                        for (int i = 0; i < functions.size(); i++)
                                        {
                                            String funcId = functions.get(i).getAttributeValue("id");
                                            if (funcId == null)
                                            {
                                                errors.add("No id found for function item " + i + " in file " + fileName);
                                            }
                                            else
                                            {
                                                allKeys.add(ImmutableList.of(rootId, groupId, funcId));
                                            }
                                        }
                                    }
                                }
                                catch(IOException | ParsingException e)
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

                            if(!type.isAnnotatedInHierarchy(this.FUNCDOC_KEY))
                            {
                                if (tree.getKind() == Kind.STRING_LITERAL)
                                {
                                    String value = tree.getValue().toString();
                                    if (allKeys.contains(ImmutableList.copyOf(value.split("/"))))
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
