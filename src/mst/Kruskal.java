package mst;

import dsu.DisjointSetUnion;
import graph.Edge;
import graph.Graph;
import java.util.ArrayList;
import java.util.Comparator;

public final class Kruskal {
    private Kruskal() { }

    public static MstResult compute(Graph graph) {
        graph.requireDirected(false);
        var sorted = new ArrayList<>(graph.edges());
        sorted.sort(Comparator.comparingInt(Edge::weight)
                .thenComparingInt(Edge::from).thenComparingInt(Edge::to));
        var dsu = new DisjointSetUnion(graph.vertexCount());
        var chosen = new ArrayList<Edge>();
        long total = 0;
        for (Edge edge : sorted) {
            if (dsu.union(edge.from(), edge.to())) {
                chosen.add(edge);
                total += edge.weight();
                if (dsu.components() == 1) break;
            }
        }
        return new MstResult(chosen, total, dsu.components());
    }
}
