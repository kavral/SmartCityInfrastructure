package mst;

import graph.Edge;
import graph.Graph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

/** Lazy edge priority queue: obsolete edges are discarded when removed. */
public final class Prim {
    private Prim() { }

    public static MstResult compute(Graph graph) {
        graph.requireDirected(false);
        boolean[] visited = new boolean[graph.vertexCount()];
        var queue = new PriorityQueue<Edge>(Comparator.comparingInt(Edge::weight)
                .thenComparingInt(Edge::from).thenComparingInt(Edge::to));
        var chosen = new ArrayList<Edge>();
        long total = 0;
        int components = 0;
        for (int start = 0; start < graph.vertexCount(); start++) {
            if (visited[start]) continue;
            components++;
            visited[start] = true;
            for (Edge edge : graph.neighbors(start)) if (!visited[edge.to()]) queue.add(edge);
            while (!queue.isEmpty()) {
                Edge next = queue.remove();
                if (visited[next.to()]) continue;
                visited[next.to()] = true;
                chosen.add(next);
                total += next.weight();
                for (Edge edge : graph.neighbors(next.to()))
                    if (!visited[edge.to()]) queue.add(edge);
            }
        }
        return new MstResult(chosen, total, components);
    }
}
