package org.ironsight.cubearray.benchmark;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {

    private static final Path CSV_PATH = Path.of("target", "benchmark-results.csv");

    public static void main(String[] args) throws Exception {
        String gitHash = gitHash();

        OptionsBuilder opt = new OptionsBuilder();
        String benchmarkFilter = System.getProperty("benchmark");
        if (benchmarkFilter != null && !benchmarkFilter.isEmpty()) {
            opt.include(".*" + benchmarkFilter + ".*");
        } else {
            opt.include(".*benchmark\\..*Benchmark");
        }
        opt.resultFormat(ResultFormatType.JSON);
        opt.result("target/jmh-results.json");
        opt.shouldFailOnError(true);
        opt.jvmArgsAppend("-Djava.awt.headless=true");

        Collection<RunResult> results = new Runner(opt.build()).run();

        appendResultsCsv(results, gitHash);

        System.out.println("\n===== CSV written to " + CSV_PATH.toAbsolutePath() + " =====");
        System.out.println("===== JMH JSON written to " + Path.of("target", "jmh-results.json").toAbsolutePath() + " =====");
    }

    private static void appendResultsCsv(Collection<RunResult> results, String gitHash) throws IOException {
        boolean exists = Files.exists(CSV_PATH);
        Files.createDirectories(CSV_PATH.getParent());

        try (FileWriter w = new FileWriter(CSV_PATH.toFile(), true)) {
            if (!exists) {
                w.write("timestamp,git_hash,benchmark,score,score_error,unit,params\n");
            }
            String timestamp = Instant.now().toString();
            for (RunResult result : results) {
                var primary = result.getPrimaryResult();
                String label = result.getParams().getBenchmark();
                double score = primary.getScore();
                double error = primary.getScoreError();
                String unit = primary.getScoreUnit();
                String params = result.getParams().getParamsKeys().stream()
                        .map(k -> k + "=" + result.getParams().getParam(k))
                        .collect(Collectors.joining(";"));
                w.write(String.format("%s,%s,%s,%.4f,%.4f,%s,%s\n",
                        timestamp, gitHash, label, score, error, unit, params));
            }
        }
    }

    private static String gitHash() {
        try {
            var p = Runtime.getRuntime().exec(new String[]{"git", "rev-parse", "--short", "HEAD"});
            byte[] out = p.getInputStream().readAllBytes();
            return new String(out).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }
}