package benchmark;

import graph.Graph;
import shortestpath.Dijkstra;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Untimed counts on exactly the road graphs used by the timing experiment. */
public final class DijkstraOperations {
    private DijkstraOperations() { }
    private static final String HEADER = "family,vertices,edges,seed,weight_checks,relaxation_attempts,successful_relaxations,queue_pushes,queue_pops,stale_pops,settled_vertices,max_queue_size,graph_sha256,result_checksum";

    private static List<String> collect() {
        List<String> rows = new ArrayList<>(); rows.add(HEADER);
        for (Benchmark.Case input : Benchmark.cases()) {
            Graph graph = GraphGenerator.connected(input.vertices(), input.edges(), input.seed());
            var profiled = Dijkstra.computeWithOperations(graph, 0);
            var ordinary = Dijkstra.compute(graph, 0);
            for (int v = 0; v < graph.vertexCount(); v++)
                Evidence.require(profiled.result().distanceTo(v) == ordinary.distanceTo(v)
                        && profiled.result().pathTo(v).equals(ordinary.pathTo(v)), "Counting changed a route");
            var c = profiled.operations();
            Evidence.require(c.weightChecks() == input.edges() && c.relaxationAttempts() == 2L * input.edges()
                    && c.settledVertices() == input.vertices(), "Road scan counts");
            Evidence.require(c.queuePushes() == c.successfulRelaxations() + 1 && c.queuePops() == c.queuePushes()
                    && c.stalePops() == c.queuePops() - c.settledVertices(), "Queue count identities");
            rows.add(input.family() + "," + input.vertices() + "," + input.edges() + "," + input.seed()
                    + "," + c.weightChecks() + "," + c.relaxationAttempts() + "," + c.successfulRelaxations()
                    + "," + c.queuePushes() + "," + c.queuePops() + "," + c.stalePops() + "," + c.settledVertices()
                    + "," + c.maximumQueueSize() + "," + Evidence.graphHash(graph) + "," + Evidence.fingerprint(profiled.result()));
        }
        return rows;
    }

    public static void write(Path root) throws IOException {
        Files.write(root.resolve("tables/dijkstra-operations.csv"), collect());
    }

    public static void verify(Path root) throws IOException {
        Evidence.require(Files.readAllLines(root.resolve("tables/dijkstra-operations.csv")).equals(collect()),
                "Dijkstra operation counts differ from regenerated graphs");
    }

    /** Per-column medians across three seeds, not repeated identical counts across JVM forks. */
    public static String table(Path root) throws IOException {
        List<String[]> rows = Files.readAllLines(root.resolve("tables/dijkstra-operations.csv")).stream()
                .skip(1).map(line -> line.split(",")).toList();
        StringBuilder table = new StringBuilder("<!-- OPERATIONS_TABLE_START -->\n\n")
                .append("Dijkstra logical operations: column medians across 3 seeds; counted outside timing. Full records: [dijkstra-operations.csv](results/tables/dijkstra-operations.csv).\n\n")
                .append("| Family | V | E / weight checks | Arc scans / relaxation attempts | Successful relaxations | Pushes | Pops | Stale pops | Settled vertices | Peak queue |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (Benchmark.Case input : Benchmark.cases()) if (input.seed() == Benchmark.SEEDS[0]) {
            List<String[]> group = rows.stream().filter(r -> r[0].equals(input.family()) && Integer.parseInt(r[1]) == input.vertices()).toList();
            Evidence.require(group.size() == 3, "Operation table requires three seeds");
            table.append("| ").append(input.family()).append(" | ").append(input.vertices()).append(" |");
            for (int column : new int[]{4, 5, 6, 7, 8, 9, 10, 11}) {
                final int index = column;
                long[] values = group.stream().mapToLong(r -> Long.parseLong(r[index])).toArray(); Arrays.sort(values);
                table.append(" ").append(values[1]).append(" |");
            }
            table.append('\n');
        }
        return table.append("\n<!-- OPERATIONS_TABLE_END -->").toString();
    }
}
