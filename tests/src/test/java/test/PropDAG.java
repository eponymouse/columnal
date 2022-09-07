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

package test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.gen.GenGraph;
import test.gen.GenGraph.Graph;
import xyz.columnal.utility.GraphUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Created by neil on 21/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropDAG
{
    // This test ignores late and does it without them:
    @Property(trials = 500)
    public void propLinearise(@From(GenGraph.class) Graph g)
    {
        List<Object> linear = GraphUtility.<Object>lineariseDAG(Collections.unmodifiableList(g.nodes), Collections.unmodifiableMap(g.incoming), new ArrayList<>());

        checkGraphLinear(g, linear);
        checkSameContent(g.nodes, linear);
    }

    private void checkSameContent(List<Object> a, List<Object> b)
    {
        Assert.assertEquals(a.size(), b.size());
        Assert.assertEquals(new HashSet<>(a), new HashSet<>(b));
    }

    @SuppressWarnings("intern")
    private void checkGraphLinear(@From(GenGraph.class) GenGraph.Graph g, List<Object> linear)
    {
        Assert.assertEquals(linear.size(), g.nodes.size());
        Assert.assertEquals(new HashSet<>(linear), new HashSet<>(g.nodes));

        // Check that all incoming links are from objects behind us:
        for (int i = 0; i < linear.size(); i++)
        {
            for (Object from : g.incoming.getOrDefault(linear.get(i), new ArrayList<>()))
            {
                // Our index must be beyond that of all incoming nodes:
                MatcherAssert.assertThat(i, Matchers.<Integer>greaterThan(linear.indexOf(from)));
            }
        }
    }

    // This property checks that late is obeyed:
    @Property(trials = 500)
    public void propLineariseLateness(@From(GenGraph.class) Graph g)
    {
        List<Object> linear = GraphUtility.<Object>lineariseDAG(Collections.unmodifiableList(g.nodes), Collections.unmodifiableMap(g.incoming), Collections.unmodifiableList(g.late));

        checkGraphLinear(g, linear);
        checkSameContent(g.nodes, linear);

        if (!g.late.isEmpty())
        {
            // Also check late nodes.
            // They should be as late as possible, which means all nodes which follow
            // a late node should have a transitive incoming edge from a late node.
            int firstLate = g.late.stream().mapToInt(i -> linear.indexOf(i)).min().orElse(-1);
            Assert.assertNotEquals(-1, firstLate);
            // Everything after first late must be late, have a link from late, or some linked node.
            // So we go through in order, checking each one and adding to validSources:
            List<Object> validSources = new ArrayList<>(g.late);
            for (int i = firstLate; i < linear.size(); i++)
            {
                Object n = linear.get(i);
                if (g.late.contains(n))
                {
                    // It's late itself; fine
                }
                else
                {
                    // Must have as a source one of validSources:
                    List<Object> in = g.incoming.get(n);
                    Assert.assertTrue(in != null && in.stream().anyMatch(x -> validSources.contains(x)));
                }
                validSources.add(n);
            }
        }
    }
    
    @Test
    public void testSpecific()
    {
        propLinearise(new Graph(ImmutableList.of("A", "B", "BL", "C", "EK", "ENU", "JR", "Nested", "VQ"), ImmutableMap.of("VQ", ImmutableList.of("BL"), "JR", ImmutableList.of("BL", "BL")), ImmutableList.of()));
    }

    @Test
    public void testSpecificLate()
    {
        propLineariseLateness(new Graph(ImmutableList.of("T2", "T9", "T6", "A", "S", "P", "E", "m"), ImmutableMap.of( "m", ImmutableList.of("T9"), "S", ImmutableList.of("T6", "E"), "T2", ImmutableList.of("A"), "T9", ImmutableList.of("T2", "S", "P")), ImmutableList.of("T2")));
        ImmutableMap.Builder<Object, List<Object>> b = ImmutableMap.builder();
        b.put("T2", ImmutableList.of("T9"));
        b.put("T9", ImmutableList.of("m"));
        b.put("T6", ImmutableList.of("S"));
        b.put("A", ImmutableList.of("T2"));
        b.put("S", ImmutableList.of("T9"));
        b.put("P", ImmutableList.of("T9"));
        b.put("E", ImmutableList.of("S"));
        
        propLineariseLateness(new Graph(ImmutableList.of("T2", "T9", "T6", "A", "S", "P", "E", "m"), b.build(), ImmutableList.of()));

        b = ImmutableMap.builder();
        b.put("T 216378", ImmutableList.of("T 963337"));
        b.put("T 963337", ImmutableList.of("merged flagbadnonwords fixdupe25"));
        b.put("T 663438", ImmutableList.of("Summary Per Participant"));
        b.put("Averages For Filtered", ImmutableList.of("T 216378"));
        b.put("Summary Per Participant", ImmutableList.of("T 963337"));
        b.put("Per Frequency All Participants", ImmutableList.of("T 963337"));
        b.put("Excluded Participants On Accuracy", ImmutableList.of("Summary Per Participant"));

        propLineariseLateness(new Graph(ImmutableList.of("T 216378", "T 963337", "T 663438", "Averages For Filtered", "Summary Per Participant", "Per Frequency All Participants", "Excluded Participants On Accuracy", "merged flagbadnonwords fixdupe25"), b.build(), ImmutableList.of()));
    }
}
