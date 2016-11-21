package test;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.GenGraph.Graph;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 21/11/2016.
 */
public class GenGraph extends Generator<Graph>
{
    public static class Graph
    {
        public List<Object> nodes;
        public Map<Object, List<Object>> incoming;

        public Graph(List<Object> nodes, Map<Object, List<Object>> incoming)
        {
            this.nodes = nodes;
            this.incoming = incoming;
        }
    }

    public GenGraph()
    {
        super(Graph.class);
    }

    @Override
    @SuppressWarnings("nullness")
    public Graph generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        int numNodes = r.nextInt(0, 15);
        // Max edges in DAG is numNodes * (numNodes - 1) / 2
        // Each node can link to all those before it.
        int numEdges = r.nextInt(0, numNodes * (numNodes - 1) / 2);

        List<Object> nodes = new ArrayList<>();
        for (int i = 0; i < numNodes; i++)
            nodes.add(new Object());
        List<Pair<Object, Object>> possibleEdges = new ArrayList<>();
        for (int i = 0; i < numNodes; i++)
            for (int j = 0; j < i; j++)
                possibleEdges.add(new Pair<>(nodes.get(j), nodes.get(i)));

        Map<Object, List<Object>> edges = new HashMap<>();
        for (Object node : nodes)
            edges.put(node, new ArrayList<>());
        // To pick the edges, we shuffle all possible edges and chop:
        Collections.shuffle(possibleEdges, r.toJDKRandom());
        for (int i = 0; i < numEdges; i++)
            // Remember, it's a map from destination to source:
            edges.get(possibleEdges.get(i).getSecond()).add(possibleEdges.get(i).getFirst());

        // Shuffle the nodes to make sure algorithm doesn't just use our ordering:
        Collections.shuffle(nodes, r.toJDKRandom());

        return new Graph(nodes, edges);
    }



}
