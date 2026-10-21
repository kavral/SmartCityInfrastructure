package mst;

import graph.Edge;
import java.util.List;

/** A minimum spanning forest when the input has multiple components. */
public record MstResult(List<Edge> edges, long totalWeight, int components) {
    public MstResult { edges = List.copyOf(edges); }
    public boolean connected() { return components <= 1; }
}
