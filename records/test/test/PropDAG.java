package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.runner.RunWith;
import test.GenGraph.Graph;
import utility.GraphUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Created by neil on 21/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropDAG
{
    // This test ignores late and does it without them:
    @Property
    public void testLinearise(@From(GenGraph.class) Graph g)
    {
        List<Object> linear = GraphUtility.<Object>lineariseDAG(Collections.unmodifiableList(g.nodes), Collections.unmodifiableMap(g.incoming), new ArrayList<>());

        checkGraphLinear(g, linear);
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
                Assert.assertThat(i, Matchers.greaterThan(linear.indexOf(from)));
            }
        }
    }

    // This property checks that late is obeyed:
    @Property
    public void testLineariseLateness(@From(GenGraph.class) Graph g)
    {
        List<Object> linear = GraphUtility.<Object>lineariseDAG(Collections.unmodifiableList(g.nodes), Collections.unmodifiableMap(g.incoming), Collections.unmodifiableList(g.late));

        checkGraphLinear(g, linear);

        if (!g.late.isEmpty())
        {
            // Also check late nodes.
            // They should be as late as possible, which means all nodes which follow
            // a late node should have a transitive incoming edge from a late node.
            int firstLate = g.late.stream().mapToInt(linear::indexOf).min().orElse(-1);
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
                    Assert.assertTrue(in != null && in.stream().anyMatch(validSources::contains));
                }
                validSources.add(n);
            }
        }
    }
}
