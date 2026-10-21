package tests;

import connectivity.Kosaraju;
import connectivity.TopologicalSort;
import dsu.DisjointSetUnion;
import graph.Edge;
import graph.Graph;
import graph.GraphIO;
import mst.Kruskal;
import mst.MstResult;
import mst.Prim;
import shortestpath.Dijkstra;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Dependency-free tests; checks run even without the JVM -ea flag. */
public final class AlgorithmTests {
    private static int checks;
    private AlgorithmTests() { }
    public static void main(String[] args) throws Exception {
        testDsu();
        testBoundaryCases();
        testDijkstraOperations();
        testExamplesAndInput();
        testGenerators();
        randomMstAgainstEnumeration();
        randomRoutesAgainstFloydWarshall();
        randomConnectivityAgainstReachability();
        exhaustiveSmallGraphs();
        longChain();
        System.out.println("PASS: " + checks + " correctness checks (fixed cases, seeded random oracles, long chain)");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void testDsu() {
        var dsu = new DisjointSetUnion(8);
        check(dsu.components() == 8, "Initial DSU components");
        check(dsu.union(0, 1) && dsu.union(2, 3) && dsu.union(1, 3), "DSU joins");
        check(!dsu.union(0, 2), "Duplicate union");
        check(dsu.find(0) == dsu.find(3) && dsu.components() == 5, "DSU membership");
        for (int i = 0; i < 20; i++) check(dsu.find(3) == dsu.find(0), "Repeated compressed find");
        rejects(() -> dsu.find(8));
        rejects(() -> new DisjointSetUnion(-1));
    }

    private static void testDijkstraOperations() {
        // Hand trace: 0 -> 1 initially costs 10, then improves through 2 to 2.
        Graph graph = new Graph(4, true);
        graph.addEdge(0, 1, 10); graph.addEdge(0, 2, 1); graph.addEdge(2, 1, 1);
        var counted = Dijkstra.computeWithOperations(graph, 0);
        check(counted.operations().equals(new Dijkstra.Operations(3, 3, 3, 4, 4, 1, 3, 2)), "Hand-counted stale queue entry");
        check(counted.result().distanceTo(1) == 2 && counted.result().distanceTo(3) == Dijkstra.INFINITY, "Counted distances");
        var single = Dijkstra.computeWithOperations(new Graph(1, false), 0);
        check(single.operations().equals(new Dijkstra.Operations(0, 0, 0, 1, 1, 0, 1, 1)), "Singleton counts");
        Graph zeros = new Graph(2, false);
        zeros.addEdge(0, 1, 0); zeros.addEdge(0, 1, 0); zeros.addEdge(0, 0, 0);
        check(Dijkstra.computeWithOperations(zeros, 0).operations().equals(new Dijkstra.Operations(3, 6, 1, 2, 2, 0, 2, 1)), "Zero cycle, duplicate and loop counts");
        Graph negative = new Graph(2, true); negative.addEdge(1, 1, -1);
        rejects(() -> Dijkstra.computeWithOperations(negative, 0));
        rejects(() -> Dijkstra.computeWithOperations(graph, -1));
        Random random = new Random(9182);
        for (int trial = 0; trial < 40; trial++) {
            Graph input = new Graph(8, trial % 2 == 0);
            for (int e = 0; e < 20; e++) input.addEdge(random.nextInt(8), random.nextInt(8), random.nextInt(6));
            var ordinary = Dijkstra.compute(input, 0); var measured = Dijkstra.computeWithOperations(input, 0);
            long reachable = 0, arcs = 0;
            for (int v = 0; v < 8; v++) {
                check(ordinary.distanceTo(v) == measured.result().distanceTo(v) && ordinary.pathTo(v).equals(measured.result().pathTo(v)), "Counters preserve result");
                if (ordinary.distanceTo(v) != Dijkstra.INFINITY) { reachable++; arcs += input.neighbors(v).size(); }
            }
            var c = measured.operations();
            check(c.weightChecks() == 20 && c.relaxationAttempts() == arcs && c.settledVertices() == reachable, "Reachable adjacency scan counts");
            check(c.queuePushes() == c.successfulRelaxations() + 1 && c.queuePops() == c.queuePushes()
                    && c.stalePops() == c.queuePops() - reachable && c.maximumQueueSize() <= c.queuePushes(), "Queue count identities");
        }
    }

    private static void testBoundaryCases() {
        Graph empty = new Graph(0, false);
        check(Kruskal.compute(empty).connected() && Prim.compute(empty).totalWeight() == 0, "Empty MST");
        check(Kosaraju.compute(new Graph(0, true)).isEmpty(), "Empty SCC");
        check(TopologicalSort.compute(new Graph(0, true)).acyclic(), "Empty DAG");
        rejects(() -> Dijkstra.compute(empty, 0));
        Graph isolated = new Graph(4, false);
        check(Kruskal.compute(isolated).components() == 4, "Isolated vertices");
        check(Prim.compute(isolated).components() == 4, "Prim isolated vertices");
        check(Dijkstra.compute(isolated, 0).pathTo(3).isEmpty(), "Unreachable path");
        check(Dijkstra.compute(isolated, 0).pathTo(0).equals(List.of(0)), "Source path");
        Graph graph = new Graph(5, false);
        graph.addEdge(0, 0, -100);
        graph.addEdge(0, 1, 7); graph.addEdge(0, 1, -2); graph.addEdge(1, 2, 0);
        graph.addEdge(3, 4, 5);
        check(Kruskal.compute(graph).totalWeight() == 3, "Negative, parallel and self edges");
        check(Prim.compute(graph).totalWeight() == 3, "Prim negative forest");
        check(!Prim.compute(graph).connected() && Prim.compute(graph).components() == 2, "Disconnected forest");
        rejects(() -> Dijkstra.compute(graph, 0));
        Graph negativeElsewhere = new Graph(3, true);
        negativeElsewhere.addEdge(1, 2, -1);
        rejects(() -> Dijkstra.compute(negativeElsewhere, 0));
        Graph large = new Graph(3, false);
        large.addEdge(0, 1, Integer.MAX_VALUE); large.addEdge(1, 2, Integer.MAX_VALUE);
        check(Kruskal.compute(large).totalWeight() == 2L * Integer.MAX_VALUE, "Long MST sum");
        check(Prim.compute(large).totalWeight() == 2L * Integer.MAX_VALUE, "Long Prim sum");
        check(Dijkstra.compute(large, 0).distanceTo(2) == 2L * Integer.MAX_VALUE, "Long route sum");
        Graph cycle = new Graph(3, true);
        cycle.addEdge(0, 1, 0); cycle.addEdge(1, 0, 0); cycle.addEdge(1, 2, 1);
        check(!TopologicalSort.compute(cycle).acyclic(), "Cycle detection");
        check(Dijkstra.compute(cycle, 0).pathTo(2).equals(List.of(0, 1, 2)), "Zero-weight cycle");
        Graph loop = new Graph(1, true); loop.addEdge(0, 0, 0);
        check(!TopologicalSort.compute(loop).acyclic(), "Self-loop cycle");
        check(Kosaraju.compute(loop).equals(List.of(List.of(0))), "Self-loop SCC");
        rejects(() -> Kruskal.compute(cycle)); rejects(() -> Prim.compute(cycle));
        rejects(() -> Kosaraju.compute(graph)); rejects(() -> TopologicalSort.compute(graph));
        rejects(() -> new Graph(-1, false)); rejects(() -> graph.addEdge(0, 5, 1));
        rejects(() -> Dijkstra.compute(large, -1));
        rejects(() -> Dijkstra.compute(large, 0).distanceTo(3));
    }

    private static void testExamplesAndInput() throws Exception {
        Graph city = GraphIO.read(java.nio.file.Path.of("examples/city.txt"));
        check(Kruskal.compute(city).totalWeight() == 11 && Prim.compute(city).totalWeight() == 11, "City MST=11");
        check(Dijkstra.compute(city, 0).distanceTo(5) == 9, "City harbor distance=9");
        check(Kosaraju.compute(GraphIO.read(java.nio.file.Path.of("examples/one-way.txt"))).size() == 3, "Example SCCs");
        verifyTopological(GraphIO.read(java.nio.file.Path.of("examples/services.txt")));
        var file = Files.createTempFile("city-graph-test-", ".txt");
        try {
            Files.writeString(file, "# comment\nundirected 2\n0 1 4 # road\n");
            check(GraphIO.read(file).edgeCount() == 1, "Comments and parsing");
            for (String invalid : List.of("", "bad 3", "directed -1", "directed 2\n0 2 4", "directed 2\n0 1", "directed x")) {
                Files.writeString(file, invalid);
                boolean rejected = false;
                try { GraphIO.read(file); } catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "Reject malformed graph");
            }
        } finally { Files.deleteIfExists(file); }
    }

    private static void randomMstAgainstEnumeration() {
        Random random = new Random(314159);
        for (int trial = 0; trial < 100; trial++) {
            int n = 2 + random.nextInt(5);
            Graph graph = new Graph(n, false);
            for (int i = 1; i < n; i++) graph.addEdge(i - 1, i, random.nextInt(15) - 5);
            for (int i = 0; i < 4; i++) graph.addEdge(random.nextInt(n), random.nextInt(n), random.nextInt(15) - 5);
            long optimum = exhaustiveTreeWeight(graph);
            check(benchmark.Evidence.referenceMst(graph) == optimum, "Array MST oracle vs enumeration");
            for (MstResult result : List.of(Kruskal.compute(graph), Prim.compute(graph))) {
                check(result.totalWeight() == optimum, "MST against exhaustive subsets");
                verifyForest(graph, result);
            }
        }
        for (int trial = 0; trial < 50; trial++) {
            Graph graph = new Graph(10, false);
            for (int i = 0; i < 15; i++) {
                int group = random.nextInt(2) * 5;
                graph.addEdge(group + random.nextInt(5), group + random.nextInt(5), random.nextInt(20));
            }
            var a = Kruskal.compute(graph); var b = Prim.compute(graph);
            check(a.totalWeight() == b.totalWeight() && a.components() == b.components(), "Forest agreement");
            verifyForest(graph, a); verifyForest(graph, b);
        }
    }

    /** Independent enumeration oracle: connectivity is checked by reachability, not DSU. */
    private static long exhaustiveTreeWeight(Graph graph) {
        long best = Long.MAX_VALUE;
        for (int mask = 0; mask < (1 << graph.edgeCount()); mask++) {
            if (Integer.bitCount(mask) != graph.vertexCount() - 1) continue;
            boolean[][] reach = new boolean[graph.vertexCount()][graph.vertexCount()];
            long weight = 0;
            for (int i = 0; i < graph.edgeCount(); i++) if ((mask & (1 << i)) != 0) {
                Edge edge = graph.edges().get(i);
                reach[edge.from()][edge.to()] = reach[edge.to()][edge.from()] = true;
                weight += edge.weight();
            }
            close(reach);
            boolean connected = true;
            for (int i = 0; i < graph.vertexCount(); i++) connected &= reach[0][i];
            if (connected) best = Math.min(best, weight);
        }
        return best;
    }

    private static void verifyForest(Graph input, MstResult result) {
        var dsu = new DisjointSetUnion(input.vertexCount());
        long total = 0;
        for (Edge edge : result.edges()) {
            check(dsu.union(edge.from(), edge.to()), "Forest is acyclic");
            check(input.edges().stream().anyMatch(e -> e.weight() == edge.weight()
                    && ((e.from() == edge.from() && e.to() == edge.to())
                    || (e.from() == edge.to() && e.to() == edge.from()))), "Forest edge exists");
            total += edge.weight();
        }
        for (Edge edge : input.edges()) check(dsu.find(edge.from()) == dsu.find(edge.to()), "Forest spans input components");
        check(result.edges().size() == input.vertexCount() - result.components(), "Forest edge count");
        check(dsu.components() == result.components() && total == result.totalWeight(), "Forest metadata");
    }

    private static void randomRoutesAgainstFloydWarshall() {
        Random random = new Random(271828);
        for (int trial = 0; trial < 80; trial++) {
            int n = 2 + random.nextInt(10);
            Graph graph = new Graph(n, trial % 2 == 0);
            long[][] distances = new long[n][n];
            long infinity = Long.MAX_VALUE / 4;
            for (int i = 0; i < n; i++) { Arrays.fill(distances[i], infinity); distances[i][i] = 0; }
            for (int i = 0; i < 2 * n; i++) graph.addEdge(random.nextInt(n), random.nextInt(n), random.nextInt(20));
            for (int i = 0; i < n; i++) for (Edge edge : graph.neighbors(i))
                distances[i][edge.to()] = Math.min(distances[i][edge.to()], edge.weight());
            for (int k = 0; k < n; k++) for (int i = 0; i < n; i++) for (int j = 0; j < n; j++)
                distances[i][j] = Math.min(distances[i][j], distances[i][k] + distances[k][j]);
            long[] reference = benchmark.Evidence.bellmanFord(graph);
            for (int target = 0; target < n; target++)
                check(reference[target] == (distances[0][target] == infinity ? Dijkstra.INFINITY : distances[0][target]), "Bellman-Ford oracle vs Floyd-Warshall");
            for (int source = 0; source < n; source++) {
                var result = Dijkstra.compute(graph, source);
                for (int target = 0; target < n; target++) {
                    long expected = distances[source][target] == infinity ? Dijkstra.INFINITY : distances[source][target];
                    check(result.distanceTo(target) == expected, "Dijkstra vs Floyd-Warshall");
                    List<Integer> path = result.pathTo(target);
                    if (expected == Dijkstra.INFINITY) { check(path.isEmpty(), "Unreachable route"); continue; }
                    check(path.get(0) == source && path.get(path.size() - 1) == target, "Route endpoints");
                    long sum = 0;
                    for (int i = 1; i < path.size(); i++) {
                        int next = path.get(i);
                        int weight = graph.neighbors(path.get(i - 1)).stream().filter(e -> e.to() == next)
                                .mapToInt(Edge::weight).min().orElseThrow();
                        sum += weight;
                    }
                    check(sum == expected, "Reconstructed route weight");
                }
            }
        }
    }

    private static void randomConnectivityAgainstReachability() {
        Random random = new Random(161803);
        for (int trial = 0; trial < 100; trial++) {
            int n = 1 + random.nextInt(15);
            Graph graph = new Graph(n, true);
            boolean[][] reach = new boolean[n][n];
            for (int i = 0; i < 2 * n; i++) {
                int from = random.nextInt(n), to = random.nextInt(n);
                graph.addEdge(from, to, 1); reach[from][to] = true;
            }
            close(reach);
            int[] component = new int[n]; Arrays.fill(component, -1);
            var groups = Kosaraju.compute(graph);
            for (int i = 0; i < groups.size(); i++) for (int vertex : groups.get(i)) {
                check(component[vertex] == -1, "SCC partition has no duplicates"); component[vertex] = i;
            }
            for (int i = 0; i < n; i++) {
                check(component[i] != -1, "SCC partition covers vertex");
                for (int j = 0; j < n; j++)
                    check((component[i] == component[j]) == (reach[i][j] && reach[j][i]), "SCC vs mutual reachability");
            }
            boolean cyclic = false;
            for (Edge edge : graph.edges()) cyclic |= reach[edge.to()][edge.from()];
            check(TopologicalSort.compute(graph).acyclic() != cyclic, "Kahn vs reachability cycle oracle");
            Graph dag = new Graph(n, true);
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++)
                if (random.nextBoolean()) dag.addEdge(i, j, 1);
            verifyTopological(dag);
        }
    }

    private static void verifyTopological(Graph graph) {
        var result = TopologicalSort.compute(graph);
        check(result.acyclic() && result.order().size() == graph.vertexCount(), "DAG ordering exists");
        int[] position = new int[graph.vertexCount()]; Arrays.fill(position, -1);
        for (int i = 0; i < result.order().size(); i++) {
            int vertex = result.order().get(i);
            check(position[vertex] == -1, "Topological permutation"); position[vertex] = i;
        }
        for (Edge edge : graph.edges()) check(position[edge.from()] < position[edge.to()], "Every dependency respected");
    }

    private static void close(boolean[][] reach) {
        for (int i = 0; i < reach.length; i++) reach[i][i] = true;
        for (int k = 0; k < reach.length; k++) for (int i = 0; i < reach.length; i++) for (int j = 0; j < reach.length; j++)
            reach[i][j] |= reach[i][k] && reach[k][j];
    }

    private static void longChain() {
        int count = 50_000;
        Graph chain = new Graph(count, true);
        for (int i = 1; i < count; i++) chain.addEdge(i - 1, i, 1);
        check(Kosaraju.compute(chain).size() == count, "Iterative SCC on long chain");
        check(TopologicalSort.compute(chain).order().size() == count, "Long DAG");
        check(Dijkstra.compute(chain, 0).distanceTo(count - 1) == count - 1, "Long route");
    }

    private static void testGenerators() {
        for (long seed : benchmark.Benchmark.SEEDS) {
            for (int edges : new int[]{400, 1237}) {
                Graph roads = benchmark.GraphGenerator.connected(100, edges, seed);
                Graph dag = benchmark.GraphGenerator.dag(100, edges, seed);
                Graph scc = benchmark.GraphGenerator.sccBlocks(100, edges, seed);
                check(roads.edgeCount() == edges && dag.edgeCount() == edges && scc.edgeCount() == edges, "Generator exact E");
                check(roads.edges().equals(benchmark.GraphGenerator.connected(100, edges, seed).edges()), "Seed reproducibility");
                check(Kruskal.compute(roads).connected(), "Generated roads connected");
                check(Kosaraju.compute(scc).size() == 5, "Planted SCC count");
                verifyTopological(dag);
                for (Graph graph : List.of(roads, dag, scc)) {
                    var pairs = new java.util.HashSet<String>();
                    for (Edge edge : graph.edges()) {
                        check(edge.from() != edge.to(), "Generator avoids loops");
                        check(pairs.add(edge.from() + ":" + edge.to()), "Generator avoids parallel edges");
                        check(edge.weight() >= 1 && edge.weight() <= 1000, "Generator weight bounds");
                    }
                }
            }
        }
    }

    /** Exhaust all 4096 loop-free directed graphs and 729 ternary undirected graphs on four vertices. */
    private static void exhaustiveSmallGraphs() {
        for (int mask = 0; mask < 4096; mask++) {
            Graph graph = new Graph(4, true); boolean[][] reach = new boolean[4][4];
            int bit = 0;
            for (int from = 0; from < 4; from++) for (int to = 0; to < 4; to++) if (from != to) {
                if ((mask & (1 << bit)) != 0) { graph.addEdge(from, to, 1); reach[from][to] = true; }
                bit++;
            }
            close(reach); int[] group = new int[4]; Arrays.fill(group, -1);
            var components = Kosaraju.compute(graph);
            for (int i = 0; i < components.size(); i++) for (int v : components.get(i)) {
                check(group[v] == -1, "Exhaustive SCC duplicate"); group[v] = i;
            }
            for (int a = 0; a < 4; a++) {
                check(group[a] >= 0, "Exhaustive SCC coverage");
                for (int b = 0; b < 4; b++) check((group[a] == group[b]) == (reach[a][b] && reach[b][a]), "Exhaustive SCC oracle");
            }
            boolean cyclic = false;
            for (Edge edge : graph.edges()) cyclic |= reach[edge.to()][edge.from()];
            check(TopologicalSort.compute(graph).acyclic() != cyclic, "Exhaustive cycle oracle");
            if (!cyclic) verifyTopological(graph);
        }
        for (int encoded = 0; encoded < 729; encoded++) {
            Graph graph = new Graph(4, false); int state = encoded;
            boolean[][] full = new boolean[4][4];
            for (int a = 0; a < 4; a++) for (int b = a + 1; b < 4; b++) {
                int choice = state % 3; state /= 3;
                if (choice != 0) { graph.addEdge(a, b, choice == 1 ? -3 : 0); full[a][b] = full[b][a] = true; }
            }
            close(full); int components = 0;
            for (int v = 0; v < 4; v++) {
                boolean first = true;
                for (int earlier = 0; earlier < v; earlier++) first &= !full[v][earlier];
                if (first) components++;
            }
            long optimum = Long.MAX_VALUE;
            for (int subset = 0; subset < (1 << graph.edgeCount()); subset++) {
                if (Integer.bitCount(subset) != 4 - components) continue;
                boolean[][] reach = new boolean[4][4]; long total = 0;
                for (int i = 0; i < graph.edgeCount(); i++) if ((subset & (1 << i)) != 0) {
                    Edge e = graph.edges().get(i); reach[e.from()][e.to()] = reach[e.to()][e.from()] = true; total += e.weight();
                }
                close(reach);
                if (Arrays.deepEquals(reach, full)) optimum = Math.min(optimum, total);
            }
            for (MstResult result : List.of(Kruskal.compute(graph), Prim.compute(graph))) {
                check(result.totalWeight() == optimum && result.components() == components, "Exhaustive minimum forest");
                verifyForest(graph, result);
            }
        }
        Graph singleton = new Graph(1, false);
        check(Kruskal.compute(singleton).connected() && Prim.compute(singleton).edges().isEmpty(), "Singleton MST");
        Graph parallel = new Graph(2, true); parallel.addEdge(0, 1, 1); parallel.addEdge(0, 1, 2);
        verifyTopological(parallel);
    }
}
