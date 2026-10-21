package graph;

/** An edge; undirected graphs store one original edge and two adjacency arcs. */
public record Edge(int from, int to, int weight) { }
