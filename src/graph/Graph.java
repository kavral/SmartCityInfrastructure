package graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Vertices are numbered 0 through vertexCount - 1. Parallel edges are allowed. */
public final class Graph {
    private final boolean directed;
    private final List<List<Edge>> adjacency;
    private final List<Edge> edges = new ArrayList<>();

    public Graph(int vertexCount, boolean directed) {
        if (vertexCount < 0) throw new IllegalArgumentException("Negative vertex count");
        this.directed = directed;
        adjacency = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) adjacency.add(new ArrayList<>());
    }

    public void addEdge(int from, int to, int weight) {
        checkVertex(from);
        checkVertex(to);
        Edge edge = new Edge(from, to, weight);
        edges.add(edge);
        adjacency.get(from).add(edge);
        if (!directed) adjacency.get(to).add(new Edge(to, from, weight));
    }

    public int vertexCount() { return adjacency.size(); }
    public int edgeCount() { return edges.size(); }
    public boolean isDirected() { return directed; }
    public List<Edge> edges() { return Collections.unmodifiableList(edges); }
    public List<Edge> neighbors(int vertex) {
        checkVertex(vertex);
        return Collections.unmodifiableList(adjacency.get(vertex));
    }

    public void checkVertex(int vertex) {
        if (vertex < 0 || vertex >= vertexCount())
            throw new IllegalArgumentException("Invalid vertex: " + vertex);
    }

    public void requireDirected(boolean expected) {
        if (directed != expected)
            throw new IllegalArgumentException(expected ? "Directed graph required" : "Undirected graph required");
    }

    public Graph transpose() {
        requireDirected(true);
        Graph reverse = new Graph(vertexCount(), true);
        for (Edge edge : edges) reverse.addEdge(edge.to(), edge.from(), edge.weight());
        return reverse;
    }
}
