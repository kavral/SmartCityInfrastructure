package connectivity;

import graph.Edge;
import graph.Graph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Kahn's algorithm. On a cyclic graph, order is only the processed prefix. */
public final class TopologicalSort {
    private TopologicalSort() { }
    public record Result(List<Integer> order, boolean acyclic) {
        public Result { order = List.copyOf(order); }
    }

    public static Result compute(Graph graph) {
        graph.requireDirected(true);
        int[] indegree = new int[graph.vertexCount()];
        for (Edge edge : graph.edges()) indegree[edge.to()]++;
        var ready = new ArrayDeque<Integer>();
        for (int v = 0; v < indegree.length; v++) if (indegree[v] == 0) ready.add(v);
        var order = new ArrayList<Integer>();
        while (!ready.isEmpty()) {
            int vertex = ready.remove();
            order.add(vertex);
            for (Edge edge : graph.neighbors(vertex))
                if (--indegree[edge.to()] == 0) ready.add(edge.to());
        }
        return new Result(order, order.size() == graph.vertexCount());
    }
}
