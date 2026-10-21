package app;

import connectivity.Kosaraju;
import connectivity.TopologicalSort;
import graph.Graph;
import graph.GraphIO;
import mst.Kruskal;
import mst.Prim;
import shortestpath.Dijkstra;
import java.nio.file.Path;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        try {
            if (args.length == 0 || (args.length == 1 && args[0].equals("demo"))) {
                System.out.println("SMART CITY: 0=Center, 1=Hospital, 2=University, 3=Station, 4=Airport, 5=Harbor");
                Graph city = new Graph(6, false);
                int[][] roads = {{0,1,4},{0,2,3},{1,2,1},{1,3,2},{2,3,4},{3,4,2},{4,5,6},{3,5,3}};
                for (int[] road : roads) city.addEdge(road[0], road[1], road[2]);
                show(city, 0);
                Graph services = new Graph(5, true);
                services.addEdge(0, 1, 1); services.addEdge(0, 2, 1);
                services.addEdge(1, 3, 1); services.addEdge(2, 3, 1); services.addEdge(3, 4, 1);
                System.out.println("\nService dependencies (DAG):");
                show(services, 0);
                services.addEdge(4, 1, 1);
                System.out.println("\nOne-way network with a cycle:");
                show(services, 0);
            } else if (args[0].equals("file") && (args.length == 2 || args.length == 3)) {
                show(GraphIO.read(Path.of(args[1])), args.length == 3 ? Integer.parseInt(args[2]) : 0);
            } else {
                throw new IllegalArgumentException("Usage: app.Main [demo | file PATH [SOURCE]]");
            }
        } catch (java.io.IOException | IllegalArgumentException error) {
            System.err.println("Error: " + error.getMessage());
            System.exit(1);
        }
    }

    private static void show(Graph graph, int source) {
        System.out.printf("%s graph: V=%d, E=%d%n", graph.isDirected() ? "Directed" : "Undirected",
                graph.vertexCount(), graph.edgeCount());
        if (graph.isDirected()) {
            var order = TopologicalSort.compute(graph);
            System.out.println(order.acyclic() ? "Topological order: " + order.order()
                    : "Cycle detected; no topological ordering exists.");
            System.out.println("Strongly connected components: " + Kosaraju.compute(graph));
        } else {
            var kruskal = Kruskal.compute(graph);
            var prim = Prim.compute(graph);
            System.out.printf("Connected: %s; components: %d%n", kruskal.connected(), kruskal.components());
            System.out.printf("%s weight: Kruskal=%d, Prim=%d%n", kruskal.connected() ? "MST" : "Forest",
                    kruskal.totalWeight(), prim.totalWeight());
            if (kruskal.totalWeight() != prim.totalWeight() || kruskal.components() != prim.components())
                throw new IllegalStateException("MST algorithms disagree");
            System.out.println("Chosen edges (Kruskal): " + kruskal.edges());
        }
        if (graph.vertexCount() == 0) { System.out.println("No vertices to route between."); return; }
        if (graph.edges().stream().anyMatch(edge -> edge.weight() < 0)) {
            System.out.println("Routes unavailable: Dijkstra requires nonnegative weights."); return;
        }
        var routes = Dijkstra.compute(graph, source);
        for (int target = 0; target < graph.vertexCount(); target++) {
            long distance = routes.distanceTo(target);
            System.out.printf("%d -> %d: distance=%s, path=%s%n", source, target,
                    distance == Dijkstra.INFINITY ? "unreachable" : Long.toString(distance), routes.pathTo(target));
        }
    }
}
