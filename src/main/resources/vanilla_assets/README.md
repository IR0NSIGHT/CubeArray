# Bundled vanilla block assets

This directory is the **default asset layer**: the complete set of vanilla Minecraft
`assets/minecraft/` JSON needed to construct a block's rendered shape, in the standard layout the
CubeArray parsers consume:

- `models/` — the geometry (block + item model JSONs). Consumed by `BlockModelParser`.
- `blockstates/` — maps each block's state (facing / half / shape / connections / …) to which
  model variant + rotation to use. This is what selects e.g. `oak_stairs_inner` (with an `x`/`y`
  rotation) for `oak_stairs[shape=inner_left,half=top]`. A texture pack never ships these either.

## Why it exists

A texture pack (e.g. the bundled Pixel Perfection) is an *overlay* — the real game merges it on
top of the vanilla assets shipped inside the client `.jar`, so a texture pack only includes the
files it changes. It typically ships **texture-only stub models** for shaped blocks, e.g.:

```json
// <pack>/assets/minecraft/models/block/sandstone_stairs.json
{ "parent": "minecraft:block/stairs", "textures": { ... } }   // no elements of its own
```

The actual geometry lives in the vanilla base models (`block/stairs`, `block/inner_stairs`,
`block/slab`, `block/template_torch`, …), which a texture pack does **not** include. CubeArray
reads only from disk with no built-in defaults, so without this layer those blocks parse to empty
geometry and render as plain cubes. This directory supplies that missing base layer so
`parent` references resolve to real cuboids.

## Provenance

Source: https://github.com/misode/mcmeta (branch `assets`), commit
`f3c3f02ac459ed8d4573143ec4f953d79fcba55c` (2026-07-07), which mirrors the extracted vanilla
Minecraft client assets.

These are Mojang's assets. They are bundled here for the tool to function; ownership remains with
Mojang/Microsoft and their use is governed by the Minecraft EULA and Usage Guidelines.
