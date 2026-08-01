# CubeArray

Minecraft schematic viewer and editor. Maven multi-module (Java 21, LWJGL 3).

## Modules

| Module | Role |
|--------|------|
| `cubearray-core` | Core library: schematic loading, block model parsing, OpenGL rendering |
| `cubearray-app` | Desktop app: Swing UI, entry point, debug tools |

## Architecture

```
File (.schem/.litematic)
  → SchemReader.prepareData()     [WPCore + BlockModelParser + BlockStateParser]
  → CubeSetup                     [data contract: palettes + positions + texture atlas]
  → InstancedCubes                [LWJGL 3, instanced rendering, GLSL shaders]
  → FileRenderApp                 [Swing UI: JTable, chip search, preview, block replacer]
```

## Key Design

- **Palette-based instanced rendering** — type data stored in 1D textures, per-face UV in 2D texture; single `glDrawElementsInstanced` call
- **Vanilla asset embedding** — block model/state JSONs bundled; resource pack (textures) user-replaceable
- **Singleton GLFW** — shared `InstancedCubes` instance with `GLFW_LOCK` for thread-safe cross-thread rendering
- **Records** — immutable data everywhere (`SubBlock`, `CubeSetup`, `CameraState`, etc.)
- **Optional LWJGL in core** — core can be consumed without native rendering libs

## Build & Run

```bash
mvn clean package -DskipTests
java -jar cubearray-app/target/CubeArray.jar              # GUI mode
java -jar cubearray-app/target/CubeArray.jar --render <file> [out] [w] [h]  # headless
```
