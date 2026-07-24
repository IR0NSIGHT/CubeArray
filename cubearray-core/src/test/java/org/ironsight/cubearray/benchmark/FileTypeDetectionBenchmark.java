package org.ironsight.cubearray.benchmark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FileTypeDetectionBenchmark {

    @Param({
        "src/test/resources/schematics/Dannypan/house_1.schem",
        "src/test/resources/schematics/Dannypan/smithy.schem",
        "src/test/resources/schematics/Ir0nsight/jerusalem_wall_arch_gates.schem"
    })
    public String schematicPath;

    private File schematicFile;

    @Setup(Level.Trial)
    public void setUp() {
        schematicFile = Path.of(schematicPath).toFile();
    }

    @Benchmark
    public String detectType() {
        return ResourceUtils.detectSchematicType(schematicFile);
    }
}