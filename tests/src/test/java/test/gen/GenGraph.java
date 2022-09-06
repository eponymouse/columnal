package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import test.gen.GenGraph.Graph;
import xyz.columnal.utility.Pair;

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
        public List<Object> late;

        public Graph(List<Object> nodes, Map<Object, List<Object>> incoming, List<Object> late)
        {
            this.nodes = nodes;
            this.incoming = incoming;
            this.late = late;
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
        int numNodes = r.nextInt(0, 20);
        // Max edges in DAG is numNodes * (numNodes - 1) / 2
        // Each node can link to all those before it.
        int numEdges = r.nextInt(0, numNodes * (numNodes - 1) / 2);
        // Keep it on the sparse side by taking lower of two picks:
        numEdges = Math.min(numEdges, r.nextInt(0, numNodes * (numNodes - 1) / 2));

        List<Object> nodes = new ArrayList<>();
        for (int i = 0; i < numNodes; i++)
            nodes.add(new Object());
        List<Pair<Object, Object>> possibleEdges = new ArrayList<>();
        for (int i = 0; i < numNodes; i++)
            for (int j = 0; j < i; j++)
            {
                // Duplicate each edge to test the algorithm for that case:
                for (int k = 0; k < 3; k++)
                {
                    possibleEdges.add(new Pair<>(nodes.get(j), nodes.get(i)));
                }
            }

        Map<Object, List<Object>> edges = new HashMap<>();
        for (Object node : nodes)
            edges.put(node, new ArrayList<>());
        // To pick the edges, we shuffle all possible edges and chop:
        Collections.shuffle(possibleEdges, r.toJDKRandom());
        for (int i = 0; i < numEdges; i++)
            // Remember, it's a map from destination to source:
            edges.get(possibleEdges.get(i).getSecond()).add(possibleEdges.get(i).getFirst());

        // Pick late by shuffling and picking:
        Collections.shuffle(nodes, r.toJDKRandom());
        int numLate = r.nextInt(0, Math.min(3, numNodes));
        List<Object> late = new ArrayList<>(nodes.subList(0, numLate));

        // Shuffle the nodes to make sure algorithm doesn't just use our ordering:
        Collections.shuffle(nodes, r.toJDKRandom());

        return new Graph(nodes, edges, late);
    }



}
