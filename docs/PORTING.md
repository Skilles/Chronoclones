# Porting guide

The repository is a [Stonecutter](https://stonecutter.kikugie.dev/) tree: one branch, one shared
`src/` tree, one Gradle node per Minecraft-version-and-loader pair. Nodes are named
`<mcversion>-<loader>` and declared in `settings.gradle.kts`; each node reads its versions from
`versions/<node>/gradle.properties` and builds with the loader's script
(`build.neoforge.gradle.kts`, `build.fabric.gradle.kts`, a future `build.forge.gradle.kts`).

## Daily work

- The active node is stored in `.sc_active_version`. Switch with
  `./gradlew "Set active project to <node>"`; this rewrites the `//?` comments in `src/` in place.
- `./gradlew runActiveClient` / `runActiveServer` / `runActiveGameTests` operate on the active node.
- Full matrix: `./gradlew chiseledBuild`, or per node `./gradlew :<node>:build :<node>:test` plus
  the gametest task (`runGameTestServer` on NeoForge, `runGametest` on Fabric).
- Loader-specific code is fenced with Stonecutter comments using the `neoforge` / `fabric` /
  `forge` constants, e.g. `//? if neoforge { ... //?} else { /*...*/ //?}`. Whole
  loader-specific files (everything under `platform/neoforge` and `platform/fabric`) are fenced
  as a unit. **Never put a `/* */` comment inside a fenced-out branch** — Java cannot nest block
  comments, so use `//` comments there.

## Where the loader differences live

- `platform/` — Registrar (registration), PlatformNetwork / PlatformClientNetwork (payload
  sends), AnchorMenus (menu-with-data), ClonePlayer (fake-player foundation), plus per-loader
  entrypoints and event bridges under `platform/neoforge` and `platform/fabric`.
- `ChronoclonesConfig` — NeoForge keeps a SERVER-type ModConfigSpec (per-world file, synced,
  config screen); Fabric reads a global `config/chronoclones.json` with the same keys and ranges.
  Keep the two branches' keys and ranges in lockstep.
- `META-INF/accesstransformer.cfg` and `chronoclones.accesswidener` are twins: every entry added
  to one must be added to the other.
- Game tests: the function table lives in `ChronoclonesGameTests`; every test also has a
  generated `src/gametest/resources/data/chronoclones/test_instance/<name>.json` carrying its
  max-tick budget. Adding a test means adding its JSON (copy a neighbour and adjust).
- Fabric event gaps (block place, use-item lifecycle, container open/close) are covered by the
  mixins in `chronoclones.fabric.mixins.json`.

## Enabling a new target

1. Uncomment (or add) the node's `match(...)` line in `settings.gradle.kts`.
2. Create `versions/<node>/gradle.properties` with `minecraft_version`,
   `minecraft_version_range`, and the loader's versions (`neo_version`, or
   `fabric_loader_version` + `fabric_api_version`, checked against
   https://fabricmc.net/develop/ and https://projects.neoforged.net/neoforged/neoforge).
3. Switch to the node and chase compile errors; version differences get `//? if` fences keyed on
   the Minecraft version (e.g. `//? if <1.21.2`), loader differences on the loader constants.

### 1.21.1 (NeoForge + Fabric)

- Obfuscated era: mojmap remapping returns. Fabric via loom + `officialMojangMappings()`
  (loom-back-compat handles the era switch; add optional Parchment); NeoForge via the same
  moddev plugin.
- Expect large deltas: `Identifier` was `ResourceLocation` (Stonecutter string replacements can
  cover the rename — see the template's `replacements.string` example), HUD layers, the render
  submit pipeline (`GuiGraphicsExtractor` and `SubmitNodeCollector` do not exist), menu APIs,
  and data-component details.
- The datapack format numbers differ; the generated test-instance JSONs and worldgen data need
  a format check.

### 1.20.1 (Forge + Fabric)

- Forge builds use `net.neoforged.moddev.legacyforge` in a new `build.forge.gradle.kts`; the
  `forge` constant already exists.
- Pre-data-component era: items carry NBT, payloads are SimpleChannel-style, gametests use the
  old annotation framework. Budget for substantial per-version fences or version-specific
  platform classes.

## Datagen

Data generation runs only from the NeoForge node (`:26.2-neoforge:runClientData`) into the
shared `src/generated/resources`, which every node includes.

## The 1.20.1 Forge node's quirks

- `at/1.20.1.cfg` is written in **srg member names**: Forge 1.20.1 applies access
  transformers before remapping to mojmap, so mojmap names silently miss. Regenerate srg ids
  from `versions/1.20.1-forge/build/moddev/artifacts/namedToIntermediate.tsrg` when the AW
  gains entries.
- `build.forge.gradle.kts` rewrites the shared `neoforge.mods.toml` into a 1.20.1
  `META-INF/mods.toml`: `loaderVersion "[47,)"`, a `forge` dependency, and — critically —
  `mandatory=true` in place of `type="required"`, without which FML rejects the mod file with
  a misleading "invalid mod file"/"Failed to find system mod: minecraft" pair.
- Forge 1.20.1 drops a mod's data pack when `pack.mcmeta` is absent
  ("Missing metadata in pack mod:chronoclones"); the build ships `compat1201/pack.mcmeta`.
- The mixin compatibility level is rewritten to `JAVA_17` (Mixin 0.8.5), and the mixin
  annotation processor is declared explicitly — the plugin only wires the refmap arguments.
- Fuel burn times route through `ForgeHooks.getBurnTime`; the stack's own `getBurnTime`
  answers `-1`, meaning "ask vanilla".
- **Dev-only, Windows-only**: Forge 1.20.1 runs fail from a working copy whose absolute path
  contains spaces. Map a drive letter (`subst X: <worktree>`) and run
  `X:\gradlew.bat :1.20.1-forge:runGameTestServer` from there. CI's space-free paths are
  unaffected.
- `UpgradeStateTest` is guarded off this node: a plain-JVM test run cannot complete Forge's
  registry event cycle, and the same test runs on the other three loaders.
