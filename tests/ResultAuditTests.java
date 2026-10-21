package tests;

import benchmark.VerifyResults;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Corrupt disposable copies and require the saved-results auditor to reject them. */
public final class ResultAuditTests {
    private ResultAuditTests() { }
    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory(Path.of("build"), "audit-negative-tests-");
        try (var files = Files.walk(Path.of("results"))) {
            for (Path file : files.toList()) {
                Path target = temporary.resolve(Path.of("results").relativize(file));
                if (Files.isDirectory(file)) Files.createDirectories(target); else Files.copy(file, target);
            }
        }
        VerifyResults.verify(temporary, false); VerifyResults.verifySeal(temporary);
        int checks = 0;
        for (String fault : List.of("summary", "duplicate", "time", "warmup", "source", "image", "operations")) {
            Path file = temporary.resolve(switch (fault) {
                case "summary" -> "tables/summary.csv";
                case "duplicate", "time" -> "tables/raw.csv";
                case "warmup" -> "logs/warmup-1.csv";
                case "source" -> "sources.sha256";
                case "operations" -> "tables/dijkstra-operations.csv";
                default -> "plots/mst-dense.png";
            });
            byte[] original = Files.readAllBytes(file);
            if (fault.equals("image")) { byte[] changed = original.clone(); changed[changed.length - 1] ^= 1; Files.write(file, changed); }
            else {
                var lines = new ArrayList<>(Files.readAllLines(file));
                String[] cells = lines.get(1).split(",");
                switch (fault) {
                    case "summary" -> { cells[4] = "123456.0"; lines.set(1, String.join(",", cells)); }
                    case "duplicate" -> lines.set(2, lines.get(1));
                    case "time" -> { cells[8] = "0"; lines.set(1, String.join(",", cells)); }
                    case "warmup" -> { cells[5] = "0"; lines.set(1, String.join(",", cells)); }
                    case "source" -> lines.set(0, "0".repeat(64) + "  " + lines.get(0).split("  ", 2)[1]);
                    case "operations" -> { cells[6] = "999999"; lines.set(1, String.join(",", cells)); }
                    default -> throw new AssertionError(fault);
                }
                Files.write(file, lines);
            }
            boolean rejected = false;
            try {
                if (fault.equals("image")) VerifyResults.verifySeal(temporary); else VerifyResults.verify(temporary, false);
            } catch (IllegalStateException expected) { rejected = true; }
            finally { Files.write(file, original); }
            if (!rejected) throw new AssertionError("Auditor accepted corrupted " + fault);
            checks++;
        }
        System.out.println("PASS: " + checks + " deliberate corruptions rejected; saved results untouched.");
    }
}
