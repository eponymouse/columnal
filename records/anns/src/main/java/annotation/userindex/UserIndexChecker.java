package annotation.userindex;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;

import java.util.function.Supplier;

/**
 * Created by neil on 11/01/2017.
 */
public class UserIndexChecker extends BaseTypeChecker
{
    /*
    private static Void time(Supplier<Void> function, boolean print, String msg)
    {
        long start = System.currentTimeMillis();
        Void v = function.get();
        long dur = System.currentTimeMillis() - start;
        if (print)
            System.err.println(msg + " took " + dur + " started " + start);
        return v;
    }
*/


    @Override
    protected BaseTypeVisitor<?> createSourceVisitor()
    {
        //System.out.println("Created source visitor out");
        //System.err.println("Created source visitor err");
        return new BaseTypeVisitor(this)
        {
            @Override
            protected GenericAnnotatedTypeFactory<?, ?, ?, ?> createTypeFactory()
            {
                return new BaseAnnotatedTypeFactory(UserIndexChecker.this, false);
            }
/*
            boolean ofInterest;
            @Override
            public void visit(TreePath path)
            {
                long start = System.currentTimeMillis();
                ofInterest = path.getCompilationUnit().getSourceFile().getName().contains("ClauseNode");
                super.visit(path);
                long dur = System.currentTimeMillis() - start;
                if (dur >= 500)
                    System.err.println("Path " + path.getCompilationUnit().getSourceFile().getName() + " took " + dur + " leaf: " + path.getLeaf().getKind());
            }

            @Override
            public Void visitMethod(MethodTree node, Void p)
            {
                return time(() -> super.visitMethod(node, p), ofInterest, "Method " + node.getName());
            }

            @Override
            public Void visitClass(ClassTree node, Void p)
            {
                return time(() -> super.visitClass(node, p), ofInterest, "Class " + node.getSimpleName());
            }
            */
        };
    }
}
