package benchmark;

import connectivity.Kosaraju;
import connectivity.TopologicalSort;
import graph.Graph;
import mst.Kruskal;
import mst.Prim;
import shortestpath.Dijkstra;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

/** Parent launches sequential fresh JVMs; workers save every unfiltered measurement. */
public final class Benchmark {
    public static final int FORKS = 3;
    public static final int WARMUPS = 10;
    public static final long WARMUP_NS = 250_000_000L;
    public static final int SAMPLES = 9;
    public static final long[] SEEDS = {42, 2025, 8675309};
    public static final List<String> ALGORITHMS = List.of("Kruskal", "Prim", "Dijkstra", "Topological", "Kosaraju");
    public static final String RAW_HEADER = "fork,case_order,family,vertices,edges,seed,algorithm,sample,elapsed_ns,graph_sha256,result_checksum";
    private static volatile long sink;
    public record Case(String family, int vertices, int edges, long seed) { }
    private record Job(String name, Supplier<Object> operation) { }
    private Benchmark() { }

    public static List<Case> cases() {
        List<Case> inputs = new ArrayList<>();
        for (String family : List.of("sparse", "dense")) {
            int[] sizes = family.equals("sparse") ? new int[]{100, 500, 1000, 3000} : new int[]{100, 200, 500, 1000};
            for (int v : sizes) for (long seed : SEEDS)
                inputs.add(new Case(family, v, family.equals("sparse") ? 4 * v : v * (v - 1) / 8, seed));
        }
        return inputs;
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length == 3 && args[0].equals("worker")) { worker(Integer.parseInt(args[1]), Path.of(args[2])); return; }
        if (args.length != 0) throw new IllegalArgumentException("Usage: benchmark.Benchmark");
        Path destination = Path.of("results"); Files.createDirectories(destination); Files.createDirectories(Path.of("build"));
        Path staged = Files.createTempDirectory(Path.of("build"), "benchmark-");
        Files.createDirectories(staged.resolve("tables")); Files.createDirectories(staged.resolve("logs"));
        writeSourceHashes(staged.resolve("sources.sha256"));
        String javaBin = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        for (int fork = 1; fork <= FORKS; fork++) {
            System.out.println("Starting JVM fork " + fork + "/" + FORKS + "; log: " + staged.resolve("logs/fork-" + fork + ".log"));
            Process child = new ProcessBuilder(javaBin, "-Xms256m", "-Xmx1g", "-cp", System.getProperty("java.class.path"),
                    Benchmark.class.getName(), "worker", Integer.toString(fork), staged.toAbsolutePath().toString())
                    .redirectErrorStream(true).redirectOutput(staged.resolve("logs/fork-" + fork + ".log").toFile()).start();
            if (child.waitFor() != 0) throw new IllegalStateException("Fork failed; inspect logs in " + staged);
        }
        List<String> raw = new ArrayList<>(); raw.add(RAW_HEADER);
        List<String> correctness = new ArrayList<>();
        for (int fork = 1; fork <= FORKS; fork++) {
            List<String> samples = Files.readAllLines(staged.resolve("tables/fork-" + fork + ".csv"));
            Evidence.require(samples.size() == 1 + cases().size() * ALGORITHMS.size() * SAMPLES, "Incomplete worker output");
            raw.addAll(samples.subList(1, samples.size()));
            List<String> checked = Files.readAllLines(staged.resolve("tables/correctness-" + fork + ".csv"));
            correctness.addAll(checked.subList(fork == 1 ? 0 : 1, checked.size()));
        }
        Files.write(staged.resolve("tables/raw.csv"), raw);
        Files.write(staged.resolve("tables/correctness.csv"), correctness);
        summarize(raw.subList(1, raw.size()), staged.resolve("tables/summary.csv"));
        StringBuilder environment = new StringBuilder("Protocol: 2; sequential fresh JVM forks\nForks: " + FORKS + "\nSeeds: 42, 2025, 8675309\n"
                + "Per graph/algorithm warmup: at least " + WARMUPS + " calls AND " + WARMUP_NS / 1_000_000 + " ms of cumulative algorithm time\n"
                + "Measured: " + SAMPLES + " calls per fork/seed/algorithm/configuration\nSummary: 81 pooled samples per cell; linear-interpolated quartiles\n"
                + "Order: Collections.shuffle(cases, new Random(91000 + fork)); rotating algorithms\n");
        for (int fork = 1; fork <= FORKS; fork++) environment.append("\n").append(Files.readString(staged.resolve("logs/environment-" + fork + ".txt")));
        Files.writeString(staged.resolve("environment.txt"), environment.toString());
        DijkstraOperations.write(staged);
        VerifyResults.verify(staged, true);
        // All computation and validation finish before the saved dataset is replaced.
        for (String directory : List.of("tables", "logs")) {
            Files.createDirectories(destination.resolve(directory));
            try (var files = Files.list(staged.resolve(directory))) {
                for (Path file : files.toList()) Files.copy(file, destination.resolve(directory).resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        for (String name : List.of("environment.txt", "sources.sha256"))
            Files.copy(staged.resolve(name), destination.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        PlotResults.main(new String[0]);
        System.out.println("Complete: " + (raw.size() - 1) + " measurements from " + FORKS + " JVMs; no observations removed.");
    }

    private static void worker(int fork, Path directory) throws IOException {
        Files.writeString(directory.resolve("logs/environment-" + fork + ".txt"), environment(fork));
        List<Case> inputs = cases(); Collections.shuffle(inputs, new Random(91000L + fork));
        try (PrintWriter raw = new PrintWriter(Files.newBufferedWriter(directory.resolve("tables/fork-" + fork + ".csv")));
             PrintWriter correctness = new PrintWriter(Files.newBufferedWriter(directory.resolve("tables/correctness-" + fork + ".csv")));
             PrintWriter warmups = new PrintWriter(Files.newBufferedWriter(directory.resolve("logs/warmup-" + fork + ".csv")))) {
            raw.println(RAW_HEADER);
            correctness.println("fork,family,vertices,edges,seed,mst_weight,distance_checksum,scc_count,road_sha256,dag_sha256,scc_sha256");
            warmups.println("fork,family,vertices,seed,algorithm,calls,cumulative_algorithm_ns");
            int caseOrder = 0;
            for (Case input : inputs) {
                caseOrder++;
                Graph road = GraphGenerator.connected(input.vertices(), input.edges(), input.seed());
                Graph dag = GraphGenerator.dag(input.vertices(), input.edges(), input.seed());
                Graph scc = GraphGenerator.sccBlocks(input.vertices(), input.edges(), input.seed());
                Evidence.Checked checked = Evidence.check(road, dag, scc);
                String roadHash = Evidence.graphHash(road), dagHash = Evidence.graphHash(dag), sccHash = Evidence.graphHash(scc);
                correctness.printf("%d,%s,%d,%d,%d,%d,%d,%d,%s,%s,%s%n", fork, input.family(), input.vertices(), input.edges(), input.seed(),
                        checked.mstWeight(), checked.distances(), checked.components(), roadHash, dagHash, sccHash);
                List<Job> jobs = List.of(new Job("Kruskal", () -> Kruskal.compute(road)), new Job("Prim", () -> Prim.compute(road)),
                        new Job("Dijkstra", () -> Dijkstra.compute(road, 0)), new Job("Topological", () -> TopologicalSort.compute(dag)),
                        new Job("Kosaraju", () -> Kosaraju.compute(scc)));
                long[] expected = new long[jobs.size()], warmNs = new long[jobs.size()]; int[] warmCalls = new int[jobs.size()];
                for (int i = 0; i < jobs.size(); i++) expected[i] = Evidence.fingerprint(jobs.get(i).operation().get());
                int round = 0; boolean warming;
                do {
                    warming = false;
                    for (int offset = 0; offset < jobs.size(); offset++) {
                        int i = (offset + round) % jobs.size();
                        if (warmCalls[i] >= WARMUPS && warmNs[i] >= WARMUP_NS) continue;
                        long begin = System.nanoTime(); Object result = jobs.get(i).operation().get(); long elapsed = System.nanoTime() - begin;
                        observe(result, expected[i]); warmCalls[i]++; warmNs[i] += elapsed; warming = true;
                    }
                    round++;
                } while (warming);
                for (int i = 0; i < jobs.size(); i++) warmups.printf("%d,%s,%d,%d,%s,%d,%d%n", fork, input.family(), input.vertices(), input.seed(), jobs.get(i).name(), warmCalls[i], warmNs[i]);
                for (int sample = 1; sample <= SAMPLES; sample++) for (int offset = 0; offset < jobs.size(); offset++) {
                    int i = (offset + sample + caseOrder) % jobs.size(); Job job = jobs.get(i);
                    long begin = System.nanoTime(); Object result = job.operation().get(); long elapsed = System.nanoTime() - begin;
                    long fingerprint = observe(result, expected[i]);
                    String graphHash = job.name().equals("Topological") ? dagHash : job.name().equals("Kosaraju") ? sccHash : roadHash;
                    raw.printf("%d,%d,%s,%d,%d,%d,%s,%d,%d,%s,%d%n", fork, caseOrder, input.family(), input.vertices(), input.edges(), input.seed(), job.name(), sample, elapsed, graphHash, fingerprint);
                }
                Evidence.require(roadHash.equals(Evidence.graphHash(road)) && dagHash.equals(Evidence.graphHash(dag)) && sccHash.equals(Evidence.graphHash(scc)), "Mutated input");
                System.out.printf("%s fork=%d case=%d/24 %s V=%d E=%d seed=%d validated%n", OffsetDateTime.now(ZoneId.of("Asia/Qyzylorda")), fork, caseOrder, input.family(), input.vertices(), input.edges(), input.seed());
            }
            raw.flush(); correctness.flush(); warmups.flush();
            if (raw.checkError() || correctness.checkError() || warmups.checkError()) throw new IOException("Cannot persist evidence");
        }
        System.out.println("Complete; observable checksum=" + sink);
    }
    private static long observe(Object result, long expected) {
        long value = Evidence.fingerprint(result); Evidence.require(value == expected, "Timed result changed"); sink = 31 * sink + value; return value;
    }
    private static void summarize(List<String> raw, Path output) throws IOException {
        var grouped = new LinkedHashMap<String, List<Long>>();
        for (String line : raw) {
            String[] c = line.split(","); String key = c[2] + "," + c[3] + "," + c[4] + "," + c[6];
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(Long.parseLong(c[8]));
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(output))) {
            out.println("family,vertices,edges,algorithm,median_ms,q1_ms,q3_ms,min_ms,max_ms,samples");
            for (Case input : cases()) if (input.seed() == SEEDS[0]) for (String algorithm : ALGORITHMS) {
                String key = input.family() + "," + input.vertices() + "," + input.edges() + "," + algorithm;
                List<Long> values = grouped.get(key); values.sort(Comparator.naturalOrder());
                out.printf("%s,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n", key, percentile(values, .5), percentile(values, .25), percentile(values, .75),
                        values.get(0) / 1e6, values.get(values.size() - 1) / 1e6, values.size());
            }
            if (out.checkError()) throw new IOException("Cannot persist summary");
        }
    }
    private static double percentile(List<Long> values, double p) {
        double position = (values.size() - 1) * p; int a = (int) position, b = (int) Math.ceil(position);
        return (values.get(a) + (values.get(b) - values.get(a)) * (position - a)) / 1e6;
    }
    static void writeSourceHashes(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String directory : List.of("src", "tests")) try (var files = Files.walk(Path.of(directory))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList())
                lines.add(sourceHash(file) + "  " + file.toString().replace('\\', '/'));
        }
        Files.write(path, lines);
    }
    static String sourceHash(Path path) throws IOException {
        return HexFormat.of().formatHex(Evidence.sha256().digest(Files.readString(path).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8)));
    }
    private static String environment(int fork) {
        return "Fork: " + fork + "\nPID: " + ProcessHandle.current().pid() + "\nRecorded: " + OffsetDateTime.now(ZoneId.of("Asia/Qyzylorda"))
                + "\nJava: " + System.getProperty("java.runtime.version") + "\nVendor: " + System.getProperty("java.vendor")
                + "\nVM: " + System.getProperty("java.vm.name") + "\nOS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\nArchitecture: " + System.getProperty("os.arch") + "\nProcessor: " + System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "not exposed")
                + "\nAvailable processors: " + Runtime.getRuntime().availableProcessors() + "\nMax heap bytes: " + Runtime.getRuntime().maxMemory()
                + "\nJVM arguments: " + ManagementFactory.getRuntimeMXBean().getInputArguments() + "\n";
    }
}
