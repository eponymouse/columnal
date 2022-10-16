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

package test.expressions;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.Expression.SaveDestination.ToEditor;
import xyz.columnal.utility.Pair;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TestDisambiguate
{
    @Test
    public void simpleLocal()
    {
        SaveDestination d = makeNames(local("local1"));
        assertEquals("local2", t(d, local("local2")));
        assertEquals("local1", t(d, local("local1")));
    }
    
    @Test
    public void localsAndColumns()
    {
        // Non-conflicting:
        SaveDestination d = makeNames(local("x"), local("y"),
            column("col1"), column("col2"), column("tab1", "col2"), column("tab1", "col3"),
            table("tab1"), table("y"));
        
        assertEquals("x", t(d, local("x")));
        assertEquals("col1", t(d, local("col1")));
        assertEquals("col1", t(d, column("col1")));
        assertEquals("col2", t(d, local("col2")));
        assertEquals("col2", t(d, column("col2")));
        assertEquals("tab1\\col2", t(d, column("tab1", "col2")));
        // Remember that now we use table references for whole columns, a column reference always refers to one of the sources:
        assertEquals("col3", t(d, column("tab1", "col3")));
        assertEquals("tab1", t(d, table("tab1")));
        assertEquals("table\\\\y", t(d, table("y")));
        assertEquals("y", t(d, local("y")));
    }
    
    private static class Name
    {
        private final @Nullable @ExpressionIdentifier String namespace;
        private final ImmutableList<@ExpressionIdentifier String> names;

        public Name(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> names)
        {
            this.namespace = namespace;
            this.names = names;
        }
    }

    private static Name n(@Nullable @ExpressionIdentifier String namespace, @ExpressionIdentifier String... names)
    {
        return new Name(namespace, ImmutableList.copyOf(names));
    }

    private static Name local(@ExpressionIdentifier String name)
    {
        return n(null, name);
    }

    private static Name column(@ExpressionIdentifier String columnNoTable)
    {
        return n("column", columnNoTable);
    }

    private static Name column(@ExpressionIdentifier String table, @ExpressionIdentifier String column)
    {
        return n("column", table, column);
    }

    private static Name table(@ExpressionIdentifier String table)
    {
        return n("table", table);
    }
    
    private static SaveDestination makeNames(Name... names)
    {
        return new ToEditor(Arrays.stream(names).<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>>map(n -> new Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>(n.namespace, n.names)).collect(ImmutableList.<Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>>>toImmutableList()));
    }
    
    private static String t(SaveDestination destination, Name name)
    {
        Pair<@Nullable @ExpressionIdentifier String, ImmutableList<@ExpressionIdentifier String>> result = destination.disambiguate(name.namespace, name.names);
        return (result.getFirst() != null ? result.getFirst() + "\\\\" : "") + result.getSecond().stream().collect(Collectors.joining("\\")); 
    }
}
