package shortestpath;

import graph.Edge;
import graph.Graph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

public final class Dijkstra {
    private Dijkstra() { }
    public static final long INFINITY = Long.MAX_VALUE;
    private record Entry(int vertex, long distance) implements Comparable<Entry> {
        public int compareTo(Entry other) { return Long.compare(distance, other.distance); }
    }

    /** Logical events, not CPU instructions or comparisons inside PriorityQueue. */
    public record Operations(long weightChecks, long relaxationAttempts, long successfulRelaxations,
                             long queuePushes, long queuePops, long stalePops,
                             long settledVertices, long maximumQueueSize) { }
    public record ProfiledResult(Result result, Operations operations) { }
    private static final class Counters {
        long weightChecks, attempts, successes, pushes, pops, stale, settled, maximumQueueSize;
        Operations snapshot() {
            return new Operations(weightChecks, attempts, successes, pushes, pops, stale, settled, maximumQueueSize);
        }
    }

    public static final class Result {
        private final long[] distances;
        private final int[] previous;
        private Result(long[] distances, int[] previous) {
            this.distances = distances;
            this.previous = previous;
        }
        public int vertexCount() { return distances.length; }
        public long distanceTo(int vertex) { checkVertex(vertex); return distances[vertex]; }
        public List<Integer> pathTo(int vertex) {
            checkVertex(vertex);
            if (distances[vertex] == INFINITY) return List.of();
            var path = new ArrayList<Integer>();
            for (int current = vertex; current != -1; current = previous[current]) path.add(current);
            Collections.reverse(path);
            return List.copyOf(path);
        }
        private void checkVertex(int vertex) {
            if (vertex < 0 || vertex >= distances.length)
                throw new IllegalArgumentException("Invalid vertex: " + vertex);
        }
    }

    public static Result compute(Graph graph, int source) {
        return compute(graph, source, null);
    }

    /** Runs the same algorithm with counters enabled; used separately from timing. */
    public static ProfiledResult computeWithOperations(Graph graph, int source) {
        Counters counters = new Counters();
        Result result = compute(graph, source, counters);
        return new ProfiledResult(result, counters.snapshot());
    }

    private static Result compute(Graph graph, int source, Counters counters) {
        graph.checkVertex(source);
        for (Edge edge : graph.edges()) {
            if (counters != null) counters.weightChecks++;
            if (edge.weight() < 0) throw new IllegalArgumentException("Dijkstra requires nonnegative weights");
        }
        long[] distances = new long[graph.vertexCount()];
        int[] previous = new int[graph.vertexCount()];
        Arrays.fill(distances, INFINITY);
        Arrays.fill(previous, -1);
        distances[source] = 0;
        var queue = new PriorityQueue<Entry>();
        queue.add(new Entry(source, 0));
        if (counters != null) { counters.pushes = 1; counters.maximumQueueSize = 1; }
        while (!queue.isEmpty()) {
            Entry current = queue.remove();
            if (counters != null) counters.pops++;
            if (current.distance() != distances[current.vertex()]) {
                if (counters != null) counters.stale++;
                continue;
            }
            if (counters != null) counters.settled++;
            for (Edge edge : graph.neighbors(current.vertex())) {
                if (counters != null) counters.attempts++;
                long candidate = current.distance() + edge.weight();
                if (candidate < distances[edge.to()]) {
                    distances[edge.to()] = candidate;
                    previous[edge.to()] = current.vertex();
                    queue.add(new Entry(edge.to(), candidate));
                    if (counters != null) {
                        counters.successes++; counters.pushes++;
                        counters.maximumQueueSize = Math.max(counters.maximumQueueSize, queue.size());
                    }
                }
            }
        }
        return new Result(distances, previous);
    }
}
