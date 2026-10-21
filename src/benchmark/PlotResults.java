package benchmark;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Uses standard Java2D; plots can be recreated without Python or external packages. */
public final class PlotResults {
    private record Row(String family, int vertices, int edges, String algorithm, double median, double q1, double q3) { }
    private static final Color[] COLORS = {new Color(35, 89, 155), new Color(192, 90, 30), new Color(33, 128, 104)};
    private PlotResults() { }
    public static void main(String[] args) throws IOException {
        VerifyResults.verify(Path.of("results"), false);
        var rows = new ArrayList<Row>();
        List<String> lines = Files.readAllLines(Path.of("results/tables/summary.csv"));
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            rows.add(new Row(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3],
                    Double.parseDouble(parts[4]), Double.parseDouble(parts[5]), Double.parseDouble(parts[6])));
        }
        Files.createDirectories(Path.of("results/plots"));
        for (String family : List.of("sparse", "dense")) {
            for (boolean byEdges : new boolean[]{false, true}) {
                plot(rows, family, List.of("Kruskal", "Prim"), "mst", byEdges);
                plot(rows, family, List.of("Dijkstra", "Topological", "Kosaraju"), "routes-connectivity", byEdges);
            }
        }
        StringBuilder table = new StringBuilder("<!-- BENCHMARK_TABLE_START -->\n\n")
                .append("Median runtime in milliseconds (81 samples per cell: 3 JVMs x 3 seeds x 9 runs). Full quartiles and ranges: [summary.csv](results/tables/summary.csv).\n\n")
                .append("| Family | V | E | Kruskal | Prim | Dijkstra | Topological | Kosaraju |\n")
                .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (Row row : rows) if (row.algorithm().equals("Kruskal")) {
            table.append(String.format(Locale.ROOT, "| %s | %d | %d |", row.family(), row.vertices(), row.edges()));
            for (String name : List.of("Kruskal", "Prim", "Dijkstra", "Topological", "Kosaraju")) {
                Row match = rows.stream().filter(r -> r.family().equals(row.family()) && r.vertices() == row.vertices()
                        && r.algorithm().equals(name)).findFirst().orElseThrow();
                table.append(String.format(Locale.ROOT, " %.4f |", match.median()));
            }
            table.append('\n');
        }
        table.append("\n<!-- BENCHMARK_TABLE_END -->");
        Files.writeString(Path.of("results/tables/summary.md"), table.toString()
                .replace("(results/tables/summary.csv)", "(summary.csv)"));
        String operationTable = DijkstraOperations.table(Path.of("results"));
        Files.writeString(Path.of("results/tables/dijkstra-operations.md"), operationTable
                .replace("(results/tables/dijkstra-operations.csv)", "(dijkstra-operations.csv)"));
        Path readme = Path.of("README.md");
        if (Files.exists(readme)) {
            String report = Files.readString(readme);
            int start = report.indexOf("<!-- BENCHMARK_TABLE_START -->");
            int end = report.indexOf("<!-- BENCHMARK_TABLE_END -->");
            if (start >= 0 && end >= start) Files.writeString(readme, report.substring(0, start) + table
                    + report.substring(end + "<!-- BENCHMARK_TABLE_END -->".length()));
            report = Files.readString(readme);
            start = report.indexOf("<!-- OPERATIONS_TABLE_START -->");
            end = report.indexOf("<!-- OPERATIONS_TABLE_END -->");
            if (start >= 0 && end >= start) Files.writeString(readme, report.substring(0, start) + operationTable
                    + report.substring(end + "<!-- OPERATIONS_TABLE_END -->".length()));
        }
        VerifyResults.seal(Path.of("results"));
        System.out.println("Rendered 8 PNG plots, refreshed timing/operation tables and recorded artifact hashes.");
    }

    private static void plot(List<Row> allRows, String family, List<String> algorithms, String kind, boolean byEdges) throws IOException {
        List<Row> rows = allRows.stream().filter(r -> r.family().equals(family) && algorithms.contains(r.algorithm())).toList();
        int width = 1100, height = 720, left = 112, right = 1020, top = 140, bottom = 560;
        double minimum = rows.stream().mapToDouble(Row::q1).min().orElseThrow();
        double maximum = rows.stream().mapToDouble(Row::q3).max().orElseThrow();
        double logMin = Math.floor(Math.log10(Math.max(minimum, 0.000001)));
        double logMax = Math.ceil(Math.log10(maximum));
        if (logMax <= logMin) logMax = logMin + 1;
        final double low = logMin, high = logMax;
        java.util.function.ToIntFunction<Row> coordinate = byEdges ? Row::edges : Row::vertices;
        int maxX = rows.stream().mapToInt(coordinate).max().orElseThrow();
        int minX = rows.stream().mapToInt(coordinate).min().orElseThrow();
        java.util.function.IntUnaryOperator x = value -> left + (int) Math.round((right - left) * (byEdges
                ? (Math.log10(value) - Math.log10(minX)) / (Math.log10(maxX) - Math.log10(minX))
                : value / (double) maxX));
        java.util.function.DoubleToIntFunction y = ms -> bottom - (int) Math.round((bottom - top)
                * (Math.log10(Math.max(ms, 0.000001)) - low) / (high - low));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(25, 35, 48)); graphics.setFont(new Font("SansSerif", Font.BOLD, 25));
        graphics.drawString((kind.equals("mst") ? "Minimum spanning tree" : "Routes and directed connectivity")
                + " - " + family + " graphs", 45, 45);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 16));
        graphics.drawString("Median algorithm runtime; bars show Q1-Q3 across 81 samples", 45, 76);
        for (int i = 0; i < algorithms.size(); i++) {
            graphics.setColor(COLORS[i]); graphics.fillRect(115 + i * 250, 101, 22, 5);
            graphics.drawString(algorithms.get(i), 146 + i * 250, 110);
        }
        for (double exponent = logMin; exponent <= logMax; exponent++) {
            double value = Math.pow(10, exponent); int pixel = y.applyAsInt(value);
            graphics.setColor(new Color(222, 227, 233)); graphics.drawLine(left, pixel, right, pixel);
            graphics.setColor(Color.DARK_GRAY);
            graphics.drawString(String.format(Locale.ROOT, exponent < -3 ? "%.0e" : exponent < 0 ? "%.3f" : "%.0f", value), 49, pixel + 5);
        }
        graphics.setColor(Color.DARK_GRAY); graphics.drawLine(left, top, left, bottom); graphics.drawLine(left, bottom, right, bottom);
        for (int value : rows.stream().mapToInt(coordinate).distinct().sorted().toArray()) {
            int pixel = x.applyAsInt(value); graphics.drawLine(pixel, bottom, pixel, bottom + 6);
            String label = Integer.toString(value);
            graphics.drawString(label, pixel - graphics.getFontMetrics().stringWidth(label) / 2, bottom + 28);
        }
        graphics.drawString(byEdges ? "Number of edges (E, logarithmic scale)" : "Number of vertices (V)", 400, 618);
        graphics.rotate(-Math.PI / 2); graphics.drawString("Runtime (ms, logarithmic scale)", -480, 25); graphics.rotate(Math.PI / 2);
        for (int i = 0; i < algorithms.size(); i++) {
            String name = algorithms.get(i);
            List<Row> series = rows.stream().filter(r -> r.algorithm().equals(name)).sorted(Comparator.comparingInt(coordinate)).toList();
            graphics.setColor(COLORS[i]); graphics.setStroke(new BasicStroke(2.2f));
            Row previous = null;
            for (Row row : series) {
                int px = x.applyAsInt(coordinate.applyAsInt(row)), py = y.applyAsInt(row.median());
                if (previous != null) graphics.drawLine(x.applyAsInt(coordinate.applyAsInt(previous)), y.applyAsInt(previous.median()), px, py);
                int q1 = y.applyAsInt(row.q1()), q3 = y.applyAsInt(row.q3());
                graphics.drawLine(px, q1, px, q3); graphics.drawLine(px - 5, q1, px + 5, q1); graphics.drawLine(px - 5, q3, px + 5, q3);
                graphics.fillOval(px - 4, py - 4, 8, 8); previous = row;
            }
        }
        graphics.setColor(Color.DARK_GRAY); graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
        graphics.drawString("Source: results/tables/summary.csv | 3 JVMs x 3 seeds x 9 runs | Generation excluded", 45, 659);
        graphics.drawString(family.equals("sparse") ? "Sparse: E = 4V. Graph topology differs by task; runtimes do not measure interchangeable work."
                : "Dense: E = floor(V(V-1)/8). Graph topology differs by task; runtimes do not measure interchangeable work.", 45, 685);
        graphics.dispose();
        ImageIO.write(image, "png", Path.of("results/plots/" + kind + "-" + family + (byEdges ? "-edges" : "") + ".png").toFile());
    }
}
