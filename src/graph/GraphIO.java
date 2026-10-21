package graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** UTF-8 edge lists: 'directed|undirected V', followed by 'from to weight'. */
public final class GraphIO {
    private GraphIO() { }
    public static Graph read(Path path) throws IOException {
        var lines = new ArrayList<String>();
        for (String raw : Files.readAllLines(path)) {
            String line = raw.split("#", 2)[0].trim();
            if (!line.isEmpty()) lines.add(line);
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("Empty graph file");
        String[] header = lines.get(0).split("\\s+");
        if (header.length != 2 || !(header[0].equals("directed") || header[0].equals("undirected")))
            throw new IllegalArgumentException("Header must be: directed|undirected V");
        Graph graph = new Graph(Integer.parseInt(header[1]), header[0].equals("directed"));
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split("\\s+");
            if (parts.length != 3) throw new IllegalArgumentException("Edge must be: from to weight");
            graph.addEdge(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        return graph;
    }
}
