package dsu;

/** Union by size and iterative full path compression. */
public final class DisjointSetUnion {
    private final int[] parent;
    private final int[] size;
    private int components;

    public DisjointSetUnion(int count) {
        if (count < 0) throw new IllegalArgumentException("Negative element count");
        parent = new int[count];
        size = new int[count];
        components = count;
        for (int i = 0; i < count; i++) { parent[i] = i; size[i] = 1; }
    }

    public int find(int element) {
        if (element < 0 || element >= parent.length)
            throw new IllegalArgumentException("Invalid DSU element: " + element);
        int root = element;
        while (parent[root] != root) root = parent[root];
        while (element != root) {
            int next = parent[element];
            parent[element] = root;
            element = next;
        }
        return root;
    }

    public boolean union(int a, int b) {
        int rootA = find(a), rootB = find(b);
        if (rootA == rootB) return false;
        if (size[rootA] < size[rootB]) { int temp = rootA; rootA = rootB; rootB = temp; }
        parent[rootB] = rootA;
        size[rootA] += size[rootB];
        components--;
        return true;
    }

    public int components() { return components; }
}
