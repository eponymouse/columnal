/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package annotation.userindex;

import annotation.userindex.qual.UnknownIfUserIndex;
import annotation.userindex.qual.UserIndex;
import annotation.userindex.qual.UserIndexBottom;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
                return new BaseAnnotatedTypeFactory(UserIndexChecker.this, false) {
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
                            UnknownIfUserIndex.class,
                            UserIndex.class,
                            UserIndexBottom.class
                        ));
                    }
                };
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
