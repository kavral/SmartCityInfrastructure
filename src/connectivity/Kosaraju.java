package connectivity;

import graph.Edge;
import graph.Graph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Both DFS passes are iterative, so long road chains cannot overflow the call stack. */
public final class Kosaraju {
    private Kosaraju() { }

    public static List<List<Integer>> compute(Graph graph) {
        graph.requireDirected(true);
        int count = graph.vertexCount();
        boolean[] visited = new boolean[count];
        int[] nextNeighbor = new int[count];
        var finish = new ArrayList<Integer>(count);
        var stack = new ArrayDeque<Integer>();
        for (int start = 0; start < count; start++) {
            if (visited[start]) continue;
            visited[start] = true;
            stack.push(start);
            while (!stack.isEmpty()) {
                int vertex = stack.peek();
                var neighbors = graph.neighbors(vertex);
                if (nextNeighbor[vertex] < neighbors.size()) {
                    int next = neighbors.get(nextNeighbor[vertex]++).to();
                    if (!visited[next]) { visited[next] = true; stack.push(next); }
                } else { finish.add(stack.pop()); }
            }
        }
        Graph reverse = graph.transpose();
        java.util.Arrays.fill(visited, false);
        var components = new ArrayList<List<Integer>>();
        for (int i = finish.size() - 1; i >= 0; i--) {
            int start = finish.get(i);
            if (visited[start]) continue;
            var component = new ArrayList<Integer>();
            stack.push(start);
            visited[start] = true;
            while (!stack.isEmpty()) {
                int vertex = stack.pop();
                component.add(vertex);
                for (Edge edge : reverse.neighbors(vertex)) {
                    if (!visited[edge.to()]) { visited[edge.to()] = true; stack.push(edge.to()); }
                }
            }
            components.add(List.copyOf(component));
        }
        return List.copyOf(components);
    }
}
