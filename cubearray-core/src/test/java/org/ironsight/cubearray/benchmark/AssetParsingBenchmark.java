package org.ironsight.cubearray.benchmark;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.ironsight.cubearray.mcmodel.BlockModel;
import org.ironsight.cubearray.mcmodel.BlockModelParser;
import org.ironsight.cubearray.mcmodel.BlockStateParser;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 3)
@Fork(1)
public class AssetParsingBenchmark {

    private Path vanillaRoot;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        ResourceUtils.copyResourcesToFile(ResourceUtils.VANILLA_ASSETS_RESOURCES);
        vanillaRoot = ResourceUtils.getInstallPath().resolve(ResourceUtils.VANILLA_ASSETS_RESOURCES);
    }

    @Benchmark
    public Map<String, BlockModel> parseModels() throws IOException {
        return BlockModelParser.parseAll(vanillaRoot);
    }

    @Benchmark
    public Map<String, BlockStateParser.BlockState> parseStates() throws IOException {
        return BlockStateParser.parseAll(vanillaRoot);
    }
}