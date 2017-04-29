package annotation.help;

import annotation.help.qual.HelpKey;
import annotation.help.qual.HelpKeyBottom;
import annotation.help.qual.UnknownIfHelp;
import com.sun.source.tree.CompilationUnitTree;
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
import org.checkerframework.javacutil.AnnotationUtils;

import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by neil on 29/04/2017.
 */
@SupportedOptions({"helpfiles"})
public class HelpFileChecker extends BaseTypeChecker
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

                return new BaseAnnotatedTypeFactory(HelpFileChecker.this, false) {
                    private final Set<String> allKeys = new HashSet<>();
                    private final List<String> errors = new ArrayList<>();

                    {
                        if(this.checker.hasOption("helpfiles")) {
                            String[] allFiles = this.checker.getOption("helpfiles").split(";");
                            Builder builder = new Builder();
                            for (String fileName : allFiles)
                            {
                                try
                                {
                                    File file = new File(fileName);
                                    Document doc = builder.build(file);
                                    Element root = doc.getRootElement();
                                    if (!root.getLocalName().equals("dialog"))
                                    {
                                        errors.add("Unknown root element: " + root.getLocalName());
                                    }
                                    String rootId = root.getAttributeValue("id");
                                    if (rootId == null || !file.getName().equals(rootId + ".xml"))
                                    {
                                        errors.add("Id of root element doesn't match file name:" + fileName + " vs " + rootId);
                                    }
                                    nu.xom.Elements helps = root.getChildElements("help");
                                    for (int i = 0; i < helps.size(); i++)
                                    {
                                        String helpId = helps.get(i).getAttributeValue("id");
                                        if (helpId == null)
                                        {
                                            errors.add("No id found for help item index " + i + " in file " + fileName);
                                        }
                                        allKeys.add(rootId + "/" + helpId);
                                    }
                                }
                                catch(IOException | ParsingException e)
                                {
                                    errors.add("Error loading " + fileName + ": " + e.getLocalizedMessage());
                                }
                            }
                        }
                    }

                    // Need to look up string literals:
                    protected TreeAnnotator createTreeAnnotator() {
                        return new ListTreeAnnotator(new TreeAnnotator[]{super.createTreeAnnotator(), new ValueTypeTreeAnnotator(this, elements)});
                    }

                    class ValueTypeTreeAnnotator extends TreeAnnotator {
                        private final AnnotationMirror HELP_KEY;

                        public ValueTypeTreeAnnotator(BaseAnnotatedTypeFactory atypeFactory, Elements elements)
                        {
                            super(atypeFactory);
                            this.HELP_KEY = AnnotationUtils.fromClass(elements, HelpKey.class);
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

                            if(!type.isAnnotatedInHierarchy(this.HELP_KEY))
                            {
                                // Empty string is automatically @Value
                                if (tree.getKind() == Kind.STRING_LITERAL)
                                {
                                    String value = tree.getValue().toString();
                                    if (allKeys.contains(value))
                                        type.addAnnotation(this.HELP_KEY);
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
                            UnknownIfHelp.class,
                            HelpKey.class,
                            HelpKeyBottom.class
                        ));
                    }
                };
            }
        };
    }
}
