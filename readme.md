# CubeArray
CubeArray is a tool to 3d render minecraft schematic files, like .schem sponge formats.
You can fly through the landscape in FPV or view it from further away.

[Github repository](https://github.com/IR0NSIGHT/CubeArray)  
[Download latest release](https://github.com/IR0NSIGHT/CubeArray/releases)

[![YouTube Showcase](./images/tumbnail_video.png)](https://youtu.be/3LYkifQ4AOs)
![](./images/screenshot_CubeArray_Quercus_robur4_1770744307148.png)
![](./images/screenshot_CubeArray_schematics_1770744334677.png)
![](./images/screenshot_CubeArray_schematics_1770744275833.png)
![](./images/showcase_3pv_01.png)
![](./images/showcase_fpv_01.png)

*THIS IS A WORK IN PROGRESS*

## What it can do
This program can take a minecraft schematic file, like myHouse.schem which you previously exported from minecraft using WorldEdit f.e. and then 3d render it in a window where you can look at it, rotate it, zoom, fly and screenshot it.

## How to use it
1. Download the program. Execute `CubeArray.exe` (no Java needed), or if you have Java 17+ installed, run `java -jar CubeArray.jar`. 
2. Select and load a schematic file.
3. A window will appear that shows your schematic. 

### Keybindings
See list at [KeyBindings.md](./KeyBindings.md)

## Supported schematic formats
1. .bo2
2. .bo3
3. .nbt
4. .schematic
5. .schem

## Requirements
### OpenGL
The program uses OpenGL so it will need a graphics card. It was developed using a GTX 1060 and RTX5060-Ti and i strongly assume all nvidia cards will work.
I have no idea if AMD cards will work.

### Operating System
The program was developed on windows 10, thats really all i can tell you.
Usually the java JVM should shield the program from OS specific quirks, but i really dont have enough experience with cross OS developement to judge if this will hold.

### Screenshots
in the install folder, you can find your screenshots:
f.e. C:\Users\MyWindowsUserName\AppData\Local\CubeArray\screenshots

you can probably use this link: [%USERPROFILE%\AppData\Local\CubeArray\screenshots](%USERPROFILE%\AppData\Local\CubeArray\screenshots) in your file explorer.

### Texture Packs
theoretically (untested) you can replace the texture pack in C:\Users\MyWindowsUser\AppData\Local\CubeArray\textures\Faithful_32x_1_21_7\assets . assuming that the assets follow a standard order, and are 32 pixels size, it should work.

## Limitations
### entities
can not display villagers, animals, etc. i think beds work? not sure.

### non-cubic block
normal 1x1x1m blocks for very well, but most special blocks like torches, fences, etc blocks will suffer and will not be displayed correctly.

### transparent blocks
transparency is very bitchy and i really dont care enough to fight 4 weeks to get it to work. sorry.

### lightning
its not raytracing, and i dont not calculate any shadows. The only lightsource is the sun and some ambient backlight.

### editing
its not a schematic editor. its a viewer.

## FAQ
- Where can i report bugs? 
  - Discord DM or make an issue https://github.com/IR0NSIGHT/CubeArray/issues
- I clicked "render" and nothing happened.
  - Its loading your schematic, but if the schematic is huge and your pc a potatoe, it will take some time. Wait at most 5 minutes.

## technical details
- scratch build in java using openGL bindings
- uses worldpainter as a library to import schematic files into the program
- uses textures from Faithful 32x - 1.21.7 resource pack: https://faithfulpack.net/
- front end is Java Swing
- 10% vibecoded. expect bugs

## Using CubeArray Core as a Maven dependency

CubeArray Core is published via [JitPack](https://jitpack.io/#IR0NSIGHT/CubeArray).  
Add the JitPack repository and the dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.IR0NSIGHT</groupId>
    <artifactId>cubearray-core</artifactId>
    <version>v1.4.4</version>
</dependency>
```

### What you get

| Package | Purpose | LWJGL required? |
|---|---|---|
| `org.ironsight.cubearray.mcmodel` | Minecraft block model / blockstate parsing | No |
| `org.ironsight.cubearray.schematic` | Schematic file reading (sponge, .schem, .schematic, .nbt, .bo2, .bo3) | No |
| `org.ironsight.cubearray.edit` | Block replacement / batch conversion utilities | No |
| `org.ironsight.cubearray.platform` | Logging, resource utils, app info | No |
| `org.ironsight.cubearray.render` | OpenGL rendering (LWJGL) | **Yes** |
| `org.ironsight.cubearray.preview` | Headless schematic preview generation (Swing + LWJGL) | **Yes** |

LWJGL is declared as `<optional>true</optional>` in the core library — it is **not** pulled transitively. If you only use `mcmodel`, `schematic`, `edit`, or `platform`, no extra setup is needed.

### Setting up LWJGL for rendering / preview

If you use the `render` or `preview` packages, add LWJGL with platform-specific natives.  
Use the LWJGL BOM and Maven OS-activated profiles:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.lwjgl</groupId>
            <artifactId>lwjgl-bom</artifactId>
            <version>3.3.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.lwjgl</groupId>
        <artifactId>lwjgl</artifactId>
    </dependency>
    <dependency>
        <groupId>org.lwjgl</groupId>
        <artifactId>lwjgl-glfw</artifactId>
    </dependency>
    <dependency>
        <groupId>org.lwjgl</groupId>
        <artifactId>lwjgl-opengl</artifactId>
    </dependency>
</dependencies>

<profiles>
    <!-- Windows natives -->
    <profile>
        <id>lwjgl-natives-windows</id>
        <activation>
            <os>
                <family>windows</family>
            </os>
        </activation>
        <dependencies>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl</artifactId>
                <classifier>natives-windows</classifier>
            </dependency>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl-glfw</artifactId>
                <classifier>natives-windows</classifier>
            </dependency>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl-opengl</artifactId>
                <classifier>natives-windows</classifier>
            </dependency>
        </dependencies>
    </profile>

    <!-- Linux natives -->
    <profile>
        <id>lwjgl-natives-linux</id>
        <activation>
            <os>
                <family>unix</family>
            </os>
        </activation>
        <dependencies>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl</artifactId>
                <classifier>natives-linux</classifier>
            </dependency>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl-glfw</artifactId>
                <classifier>natives-linux</classifier>
            </dependency>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl-opengl</artifactId>
                <classifier>natives-linux</classifier>
            </dependency>
        </dependencies>
    </profile>

    <!-- macOS natives (x86_64) -->
    <profile>
        <id>lwjgl-natives-macos</id>
        <activation>
            <os>
                <family>mac</family>
            </os>
        </activation>
        <dependencies>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl</artifactId>
                <classifier>natives-macos</classifier>
            </dependency>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl-glfw</artifactId>
                <classifier>natives-macos</classifier>
            </dependency>
            <dependency>
                <groupId>org.lwjgl</groupId>
                <artifactId>lwjgl-opengl</artifactId>
                <classifier>natives-macos</classifier>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

For Apple Silicon (ARM64) Macs, replace `natives-macos` with `natives-macos-arm64` in the macOS profile, or include both classifiers.

> **Note:** CubeArray has only been tested on Windows and Linux. macOS support via LWJGL should work in theory but is untested.

### Transitive dependencies

CubeArray Core depends on:
- **JOML** (`org.joml:joml:1.10.5`) — available from Maven Central
- **WPCore** (`org.pepsoft.worldpainter:WPCore:2.25.0`) — requires the [EngineHub repository](https://maven.enginehub.org/repo/)
- **SchemConvert** (`com.github.PiTheGuy:SchemConvert:v1.2.5`) — published on JitPack
- **Jackson** (`com.fasterxml.jackson.core:jackson-databind:2.19.1`) — available from Maven Central
- **Commons IO** (`commons-io:commons-io:2.14.0`) — available from Maven Central

If your project has resolution issues with `WPCore`, add the EngineHub repository to your `pom.xml`:

```xml
<repository>
    <id>enginehub</id>
    <url>https://maven.enginehub.org/repo/</url>
</repository>
```

### Quick usage example

```java
import org.ironsight.cubearray.schematic.SchemReader;
import org.pepsoft.worldpainter.objects.WPObject;
import java.io.File;
import java.util.List;

public class Example {
    public static void main(String[] args) throws Exception {
        SchemReader reader = new SchemReader();
        List<WPObject> objects = reader.loadSchematic(new File("build.schem"));
        System.out.println("Loaded " + objects.size() + " objects");
    }
}
```
