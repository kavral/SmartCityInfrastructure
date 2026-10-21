package benchmark;

import connectivity.Kosaraju;
import connectivity.TopologicalSort;
import graph.Edge;
import graph.Graph;
import mst.Kruskal;
import mst.MstResult;
import mst.Prim;
import shortestpath.Dijkstra;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** Untimed validation and fingerprints. Reference algorithms use neither DSU nor a heap. */
public final class Evidence {
    private Evidence() { }
    public record Checked(long mstWeight, long distances, int components) { }

    public static Checked check(Graph road, Graph dag, Graph scc) {
        long optimum = referenceMst(road);
        checkTree(road, Kruskal.compute(road), optimum);
        checkTree(road, Prim.compute(road), optimum);
        long[] expected = bellmanFord(road);
        var routes = Dijkstra.compute(road, 0);
        for (int v = 0; v < road.vertexCount(); v++) {
            require(expected[v] != Dijkstra.INFINITY && routes.distanceTo(v) == expected[v], "Dijkstra vs Bellman-Ford");
            List<Integer> path = routes.pathTo(v);
            require(!path.isEmpty() && path.get(0) == 0 && path.get(path.size() - 1) == v, "Route endpoints");
            long weight = 0;
            for (int i = 1; i < path.size(); i++) {
                int to = path.get(i);
                weight += road.neighbors(path.get(i - 1)).stream().filter(e -> e.to() == to)
                        .mapToInt(Edge::weight).min().orElseThrow();
            }
            require(weight == expected[v], "Route weight");
        }
        var order = TopologicalSort.compute(dag);
        require(order.acyclic() && order.order().size() == dag.vertexCount(), "DAG size");
        int[] position = new int[dag.vertexCount()]; Arrays.fill(position, -1);
        for (int i = 0; i < position.length; i++) {
            int v = order.order().get(i);
            require(v >= 0 && v < position.length && position[v] == -1, "DAG permutation"); position[v] = i;
        }
        for (Edge e : dag.edges()) require(position[e.from()] < position[e.to()], "DAG dependency");
        var groups = Kosaraju.compute(scc);
        require(groups.size() == scc.vertexCount() / 20, "SCC count");
        boolean[] seen = new boolean[scc.vertexCount()];
        for (var group : groups) {
            require(group.size() == 20, "SCC block size");
            int block = group.get(0) / 20;
            for (int v : group) {
                require(v >= 0 && v < seen.length && !seen[v] && v / 20 == block, "Exact planted SCC membership");
                seen[v] = true;
            }
        }
        for (boolean covered : seen) require(covered, "SCC coverage");
        return new Checked(optimum, fingerprint(routes), groups.size());
    }

    /** O(V^2 + E) array-based Prim, used only as an independent implementation oracle. */
    public static long referenceMst(Graph graph) {
        graph.requireDirected(false);
        long[] key = new long[graph.vertexCount()]; Arrays.fill(key, Long.MAX_VALUE);
        boolean[] used = new boolean[key.length];
        if (key.length == 0) return 0;
        key[0] = 0; long total = 0;
        for (int step = 0; step < key.length; step++) {
            int best = -1;
            for (int v = 0; v < key.length; v++) if (!used[v] && (best == -1 || key[v] < key[best])) best = v;
            require(key[best] != Long.MAX_VALUE, "Reference expects connected road graph");
            total += key[best]; used[best] = true;
            for (Edge edge : graph.neighbors(best)) if (!used[edge.to()]) key[edge.to()] = Math.min(key[edge.to()], edge.weight());
        }
        return total;
    }

    /** Edge relaxation oracle with early stopping. Benchmark weights are nonnegative. */
    public static long[] bellmanFord(Graph graph) {
        long[] distances = new long[graph.vertexCount()]; Arrays.fill(distances, Dijkstra.INFINITY);
        graph.checkVertex(0); distances[0] = 0;
        for (int pass = 1; pass < graph.vertexCount(); pass++) {
            boolean changed = false;
            for (int from = 0; from < graph.vertexCount(); from++) {
                if (distances[from] == Dijkstra.INFINITY) continue;
                for (Edge edge : graph.neighbors(from)) {
                    long candidate = distances[from] + edge.weight();
                    if (candidate < distances[edge.to()]) { distances[edge.to()] = candidate; changed = true; }
                }
            }
            if (!changed) break;
        }
        return distances;
    }

    private static void checkTree(Graph graph, MstResult result, long optimum) {
        require(result.connected() && result.components() == 1 && result.edges().size() == graph.vertexCount() - 1, "Tree dimensions");
        HashSet<Edge> available = new HashSet<>(graph.edges());
        Graph tree = new Graph(graph.vertexCount(), false); long total = 0;
        for (Edge edge : result.edges()) {
            require(available.contains(edge) || available.contains(new Edge(edge.to(), edge.from(), edge.weight())), "Tree edge exists");
            tree.addEdge(edge.from(), edge.to(), edge.weight()); total += edge.weight();
        }
        boolean[] visited = new boolean[graph.vertexCount()];
        ArrayDeque<Integer> pending = new ArrayDeque<>(); pending.add(0); visited[0] = true;
        while (!pending.isEmpty()) for (Edge edge : tree.neighbors(pending.remove()))
            if (!visited[edge.to()]) { visited[edge.to()] = true; pending.add(edge.to()); }
        for (boolean reached : visited) require(reached, "Tree covers every vertex");
        require(total == result.totalWeight() && total == optimum, "MST vs array-based reference");
    }

    public static String graphHash(Graph graph) {
        MessageDigest digest = sha256(); ByteBuffer bytes = ByteBuffer.allocate(12);
        digest.update(bytes.putInt(graph.vertexCount()).putInt(graph.edgeCount()).putInt(graph.isDirected() ? 1 : 0).array());
        for (Edge edge : graph.edges()) {
            bytes.clear(); digest.update(bytes.putInt(edge.from()).putInt(edge.to()).putInt(edge.weight()).array());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Order-sensitive 64-bit checksum of the returned result, not a timing. */
    public static long fingerprint(Object result) {
        long hash = 17;
        if (result instanceof MstResult mst) {
            hash = 31 * hash + mst.totalWeight(); hash = 31 * hash + mst.components();
            for (Edge edge : mst.edges()) { hash = 31 * hash + edge.from(); hash = 31 * hash + edge.to(); hash = 31 * hash + edge.weight(); }
        } else if (result instanceof Dijkstra.Result routes) {
            for (int v = 0; v < routes.vertexCount(); v++) hash = 31 * hash + routes.distanceTo(v);
        } else if (result instanceof TopologicalSort.Result order) {
            hash = 31 * hash + (order.acyclic() ? 1 : 0);
            for (int v : order.order()) hash = 31 * hash + v;
        } else if (result instanceof List<?> groups) {
            for (Object item : groups) {
                List<?> group = (List<?>) item; hash = 31 * hash + group.size();
                for (Object vertex : group) hash = 31 * hash + (Integer) vertex;
            }
        } else throw new IllegalArgumentException("Unknown result");
        return hash;
    }
    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
