package org.ironsight.cubearray.benchmark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.schematic.SchemReader;
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
import org.pepsoft.worldpainter.objects.WPObject;

@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 3)
@Fork(1)
public class SchematicLoadingBenchmark {

    @Param({
        "src/test/resources/schematics/Dannypan/house_1.schem",
        "src/test/resources/schematics/Dannypan/smithy.schem",
        "src/test/resources/schematics/Ir0nsight/jerusalem_wall_arch_gates.schem"
    })
    public String schematicPath;

    private List<WPObject> wpObjects;
    private boolean setupDone = false;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
        ResourceUtils.copyResourcesToFile(ResourceUtils.VANILLA_ASSETS_RESOURCES);

        List<Path> pathList = List.of(Path.of(schematicPath));
        wpObjects = SchemReader.loadSchematics(pathList, f -> {});

        SchemReader.prepareData(wpObjects);
        setupDone = true;
    }

    @Benchmark
    public CubeSetup loadAndPrepare() throws Exception {
        List<Path> pathList = List.of(Path.of(schematicPath));
        List<WPObject> objects = SchemReader.loadSchematics(pathList, f -> {});
        return SchemReader.prepareData(objects);
    }
}