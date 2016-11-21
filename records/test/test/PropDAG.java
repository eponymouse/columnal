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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Created by neil on 21/11/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropDAG
{
    @Property
    @SuppressWarnings("intern")
    public void testLinearise(@From(GenGraph.class) Graph g)
    {
        List<Object> linear = GraphUtility.<Object>lineariseDAG(g.nodes, g.incoming, new ArrayList<>());

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
}
