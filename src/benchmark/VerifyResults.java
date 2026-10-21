package benchmark;

import connectivity.Kosaraju;
import connectivity.TopologicalSort;
import graph.Graph;
import mst.Kruskal;
import mst.Prim;
import shortestpath.Dijkstra;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Independently recomputes input hashes, answers and summary statistics from saved raw data. */
public final class VerifyResults {
    private VerifyResults() { }
    private record Expected(String roadHash, String dagHash, String sccHash, Evidence.Checked checked, Map<String, Long> results) { }
    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Path.of("results") : Path.of(args[0]);
        verify(root, true);
        verifySeal(root);
        if (root.equals(Path.of("results")) && Files.exists(Path.of("README.md"))) {
            String readme = Files.readString(Path.of("README.md"));
            String table = Files.readString(root.resolve("tables/summary.md")).replace("(summary.csv)", "(results/tables/summary.csv)");
            Evidence.require(readme.replace("\r\n", "\n").contains(table.replace("\r\n", "\n")), "README table differs from saved table");
            Evidence.require(readme.replace("\r\n", "\n").contains(DijkstraOperations.table(root)), "README operation table differs");
        }
        System.out.println("PASS: source hashes, 72 regenerated graphs, 3240 samples, 40 summaries, 360 warmup records, 24 operation records, file integrity and README tables.");
        System.out.println("Timing values cannot be authenticated cryptographically; these checks establish consistency and reproducibility, not independent attestation.");
    }

    public static void verify(Path root, boolean regenerate) throws IOException {
        Set<String> sourceFiles = new HashSet<>();
        for (String directory : List.of("src", "tests")) try (var paths = Files.walk(Path.of(directory))) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".java")).toList()) sourceFiles.add(p.toString().replace('\\', '/'));
        }
        Set<String> recordedSources = new HashSet<>();
        for (String line : Files.readAllLines(root.resolve("sources.sha256"))) {
            String[] entry = line.split("  ", 2);
            Evidence.require(entry.length == 2 && recordedSources.add(entry[1]), "Invalid source manifest");
            Evidence.require(entry[0].equals(Benchmark.sourceHash(Path.of(entry[1]))), "Source changed since benchmark: " + entry[1]);
        }
        Evidence.require(sourceFiles.equals(recordedSources), "Source manifest coverage differs");
        DijkstraOperations.verify(root);
        Map<Benchmark.Case, Expected> expected = new HashMap<>();
        Set<Benchmark.Case> allowed = new HashSet<>(Benchmark.cases());
        if (regenerate) for (Benchmark.Case c : Benchmark.cases()) {
            Graph road = GraphGenerator.connected(c.vertices(), c.edges(), c.seed());
            Graph dag = GraphGenerator.dag(c.vertices(), c.edges(), c.seed());
            Graph scc = GraphGenerator.sccBlocks(c.vertices(), c.edges(), c.seed());
            Evidence.Checked checked = Evidence.check(road, dag, scc);
            Map<String, Long> fingerprints = Map.of("Kruskal", Evidence.fingerprint(Kruskal.compute(road)), "Prim", Evidence.fingerprint(Prim.compute(road)),
                    "Dijkstra", Evidence.fingerprint(Dijkstra.compute(road, 0)), "Topological", Evidence.fingerprint(TopologicalSort.compute(dag)),
                    "Kosaraju", Evidence.fingerprint(Kosaraju.compute(scc)));
            expected.put(c, new Expected(Evidence.graphHash(road), Evidence.graphHash(dag), Evidence.graphHash(scc), checked, fingerprints));
        }
        Map<Integer, List<Benchmark.Case>> order = new HashMap<>();
        for (int fork = 1; fork <= Benchmark.FORKS; fork++) {
            List<Benchmark.Case> inputs = Benchmark.cases(); Collections.shuffle(inputs, new Random(91000L + fork)); order.put(fork, inputs);
        }
        List<String> raw = Files.readAllLines(root.resolve("tables/raw.csv"));
        Evidence.require(raw.size() == 3241 && raw.get(0).equals(Benchmark.RAW_HEADER), "Raw header/count");
        Set<String> unique = new HashSet<>(); Map<String, List<Long>> groups = new HashMap<>();
        for (String line : raw.subList(1, raw.size())) {
            String[] c = line.split(","); Evidence.require(c.length == 11, "Raw columns");
            int fork = Integer.parseInt(c[0]), position = Integer.parseInt(c[1]), sample = Integer.parseInt(c[7]);
            Benchmark.Case input = new Benchmark.Case(c[2], Integer.parseInt(c[3]), Integer.parseInt(c[4]), Long.parseLong(c[5]));
            Evidence.require(allowed.contains(input) && Benchmark.ALGORITHMS.contains(c[6]), "Unexpected raw input");
            Evidence.require(fork >= 1 && fork <= Benchmark.FORKS && position >= 1 && position <= 24, "Fork/order range");
            Evidence.require(order.get(fork).get(position - 1).equals(input), "Case shuffle mismatch");
            Evidence.require(sample >= 1 && sample <= Benchmark.SAMPLES && Long.parseLong(c[8]) > 0, "Sample/time range");
            Evidence.require(unique.add(fork + ":" + input + ":" + c[6] + ":" + sample), "Duplicate sample");
            if (regenerate) {
                Expected e = expected.get(input);
                String hash = c[6].equals("Topological") ? e.dagHash() : c[6].equals("Kosaraju") ? e.sccHash() : e.roadHash();
                Evidence.require(c[9].equals(hash) && Long.parseLong(c[10]) == e.results().get(c[6]), "Graph/result differs from regeneration");
            }
            String key = c[2] + "," + c[3] + "," + c[4] + "," + c[6];
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(Long.parseLong(c[8]));
        }
        List<String> summary = Files.readAllLines(root.resolve("tables/summary.csv"));
        Evidence.require(summary.size() == 41 && groups.size() == 40, "Summary count");
        Set<String> summaries = new HashSet<>();
        for (String line : summary.subList(1, summary.size())) {
            String[] c = line.split(","); Evidence.require(c.length == 10, "Summary columns");
            String key = String.join(",", Arrays.copyOf(c, 4));
            Evidence.require(summaries.add(key) && groups.containsKey(key), "Unknown/duplicate summary");
            long[] sorted = groups.get(key).stream().mapToLong(Long::longValue).sorted().toArray();
            Evidence.require(sorted.length == 81 && Integer.parseInt(c[9]) == 81, "Expected 81 samples");
            // 81 samples: linear interpolation ranks are exact integers 20, 40 and 60.
            int[] indices = {40, 20, 60, 0, 80};
            for (int i = 0; i < indices.length; i++)
                Evidence.require(Math.abs(Double.parseDouble(c[4 + i]) - sorted[indices[i]] / 1e6) <= .00000051, "Incorrect summary statistic: " + key);
        }
        List<String> correctness = Files.readAllLines(root.resolve("tables/correctness.csv"));
        Evidence.require(correctness.size() == 73, "Correctness record count");
        Set<String> correctnessIds = new HashSet<>();
        for (String line : correctness.subList(1, correctness.size())) {
            String[] c = line.split(",");
            Benchmark.Case input = new Benchmark.Case(c[1], Integer.parseInt(c[2]), Integer.parseInt(c[3]), Long.parseLong(c[4]));
            int fork = Integer.parseInt(c[0]);
            Evidence.require(fork >= 1 && fork <= 3 && allowed.contains(input) && correctnessIds.add(fork + ":" + input), "Correctness identity");
            if (regenerate) {
                Expected e = expected.get(input);
                Evidence.require(Long.parseLong(c[5]) == e.checked().mstWeight() && Long.parseLong(c[6]) == e.checked().distances()
                        && Integer.parseInt(c[7]) == e.checked().components() && c[8].equals(e.roadHash()) && c[9].equals(e.dagHash()) && c[10].equals(e.sccHash()), "Incorrect saved answer");
            }
        }
        for (int fork = 1; fork <= 3; fork++) {
            final int id = fork;
            List<String> child = Files.readAllLines(root.resolve("tables/fork-" + fork + ".csv"));
            Evidence.require(child.subList(1, child.size()).equals(raw.subList(1, raw.size()).stream().filter(s -> s.startsWith(id + ",")).toList()), "Raw differs from worker file");
            List<String> warm = Files.readAllLines(root.resolve("logs/warmup-" + fork + ".csv"));
            Evidence.require(warm.size() == 121, "Warmup count");
            Set<String> warmed = new HashSet<>();
            for (String line : warm.subList(1, warm.size())) {
                String[] c = line.split(",");
                Evidence.require(Integer.parseInt(c[0]) == fork && Benchmark.ALGORITHMS.contains(c[4]), "Warmup identity");
                Evidence.require(allowed.stream().anyMatch(v -> v.family().equals(c[1]) && v.vertices() == Integer.parseInt(c[2]) && v.seed() == Long.parseLong(c[3])), "Unknown warmup graph");
                Evidence.require(warmed.add(String.join(",", Arrays.copyOf(c, 5))), "Duplicate warmup");
                Evidence.require(Integer.parseInt(c[5]) >= Benchmark.WARMUPS && Long.parseLong(c[6]) >= Benchmark.WARMUP_NS, "Insufficient warmup");
            }
        }
    }

    public static void seal(Path root) throws IOException {
        List<String> hashes = new ArrayList<>();
        for (Path file : artifactFiles(root)) hashes.add(fileHash(file) + "  " + root.relativize(file).toString().replace('\\', '/'));
        Files.write(root.resolve("integrity.sha256"), hashes);
    }
    public static void verifySeal(Path root) throws IOException {
        Set<String> recorded = new HashSet<>();
        for (String line : Files.readAllLines(root.resolve("integrity.sha256"))) {
            String[] c = line.split("  ", 2); Path file = root.resolve(c[1]).normalize();
            Evidence.require(file.toAbsolutePath().startsWith(root.toAbsolutePath().normalize()) && recorded.add(c[1]), "Invalid artifact path");
            Evidence.require(c[0].equals(fileHash(file)), "Artifact changed: " + c[1]);
        }
        Set<String> actual = new HashSet<>();
        for (Path file : artifactFiles(root)) actual.add(root.relativize(file).toString().replace('\\', '/'));
        Evidence.require(actual.equals(recorded), "Artifact manifest coverage differs");
    }
    private static List<Path> artifactFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals("integrity.sha256")
                    && !p.getFileName().toString().equals("audit.txt")).sorted().toList();
        }
    }
    private static String fileHash(Path file) throws IOException {
        byte[] bytes = file.toString().endsWith(".png") ? Files.readAllBytes(file)
                : Files.readString(file).replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(Evidence.sha256().digest(bytes));
    }
}
