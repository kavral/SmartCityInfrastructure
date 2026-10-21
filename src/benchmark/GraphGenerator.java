package benchmark;

import graph.Graph;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Simple, seeded graphs with exact edge counts; generation is never timed. */
public final class GraphGenerator {
    private GraphGenerator() { }
    public static Graph connected(int vertices, int edges, long seed) {
        validate(vertices, edges);
        Graph graph = new Graph(vertices, false);
        Random random = new Random(seed);
        Set<Long> used = new HashSet<>();
        for (int vertex = 1; vertex < vertices; vertex++)
            add(graph, used, vertex, random.nextInt(vertex), random);
        fill(graph, used, edges, random, false);
        return graph;
    }

    public static Graph dag(int vertices, int edges, long seed) {
        validate(vertices, edges);
        Graph graph = new Graph(vertices, true);
        Random random = new Random(seed);
        Set<Long> used = new HashSet<>();
        for (int vertex = 1; vertex < vertices; vertex++) add(graph, used, vertex - 1, vertex, random);
        fill(graph, used, edges, random, false);
        return graph;
    }

    /** Rings of up to 20 vertices; cross-block arcs always point to a later block. */
    public static Graph sccBlocks(int vertices, int edges, long seed) {
        validate(vertices, edges);
        if (vertices % 20 != 0 || edges < vertices)
            throw new IllegalArgumentException("SCC benchmark needs a multiple of 20 vertices and E >= V");
        Graph graph = new Graph(vertices, true);
        Random random = new Random(seed);
        Set<Long> used = new HashSet<>();
        for (int start = 0; start < vertices; start += 20)
            for (int offset = 0; offset < 20; offset++)
                add(graph, used, start + offset, start + (offset + 1) % 20, random);
        fill(graph, used, edges, random, true);
        return graph;
    }

    private static void validate(int vertices, int edges) {
        if (vertices < 2 || edges < vertices - 1 || edges > (long) vertices * (vertices - 1) / 2)
            throw new IllegalArgumentException("Invalid benchmark size");
    }

    private static void fill(Graph graph, Set<Long> used, int count, Random random, boolean blocks) {
        while (graph.edgeCount() < count) {
            int from = random.nextInt(graph.vertexCount()), to = random.nextInt(graph.vertexCount());
            if (from == to) continue;
            if ((blocks && from / 20 > to / 20) || (!blocks && from > to)) {
                int swap = from; from = to; to = swap;
            }
            add(graph, used, from, to, random);
        }
    }

    private static void add(Graph graph, Set<Long> used, int from, int to, Random random) {
        if (!graph.isDirected() && from > to) { int swap = from; from = to; to = swap; }
        long key = ((long) from << 32) | (to & 0xffffffffL);
        if (used.add(key)) graph.addEdge(from, to, 1 + random.nextInt(1000));
    }
}
