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

package annotation;

import annotation.qual.ImmediateValue;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.qual.ValueBottom;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;

import java.lang.annotation.Annotation;
import java.util.Arrays;
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
                            ImmediateValue.class,
                            Value.class,
                            ValueBottom.class
                        ));
                    }
                };
            }
        };
    }
}
