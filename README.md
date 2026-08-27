
![Chronoclones banner](https://media.forgecdn.net/attachments/1831/865/chronoclones-banner-png.png) 
[![CurseForge Game Versions](https://img.shields.io/curseforge/game-versions/1625743?style=for-the-badge&logo=curseforge&label=%20&color=black)](https://www.curseforge.com/minecraft/mc-mods/chronoclones)
[![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/MJ6ISH7f?style=for-the-badge&logo=modrinth&label=%20&color=rgb(25%2025%2035))](https://modrinth.com/mod/chronoclones)
![GitHub Release](https://img.shields.io/github/v/release/Skilles/Chronoclones?include_prereleases&sort=semver&display_name=tag&style=for-the-badge&logo=github&color=%232088FF&link=https%3A%2F%2Fgithub.com%2FSkilles%2FChronoclones%2Freleases)


Record yourself moving items, killing mobs, breaking blocks, building a house, or pretty much anything. Then have an army of clones do it time and time again.

![Chronoclones demo](https://media.forgecdn.net/attachments/1832/283/chronoclones-demo-web-gif.gif)
![Chronoclones showcase](https://media.forgecdn.net/attachments/1834/97/chronoclones-showcase-png.png)

***

# **Getting Started**

This mod adds a single block and several items to the game. To start, you'll want three things:

*   **Chrono Recorder** — right-click to start or stop recording
*   **Chrono Anchor** — where your recording plays from
*   **Chrono Goggles** — see previews of nearby recordings

![recipes](https://media.forgecdn.net/attachments/1834/297/chronoclones-recipes-png.png)

## **Supported Interactions**

*   Break/place blocks
*   Interacting with blocks
*   Moving items around
*   Crafting items
*   Trade with villagers
*   Enchant and anvil items
*   Physical movement
*   Attacking and killing mobs
*   Interacting with mobs, including shearing, feeding, and more
*   **And more…**

If I find an interaction isn't supported, I'll probably add it!

## **Upgrades**

*   **Chrono Accelerator** - make your recording play faster
*   **Chrono Splitter** - adds more clones to a recording

## **Configuration**

*   `maxRadius` - max distance a clone can act from an anchor
*   `maxRecordingTicks` - how long a recording can go on for
*   `maxActions` - max number of actions in a recording
*   `maxActionsPerTick` - per-level budget of actions per tick
*   `maxActionTicks` - max number of ticks an action can take
*   `allowPvp` - whether clones can attack players
*   `goggleRadius` - how far the goggles reveal recordings
*   `gogglesShowOthers` - whether goggles show other players' recordings  
    See config file for more info

***

**Currently NeoForge and Fabric, Minecraft 26.2**

**Warning**: Since these clones mimic real players, expect weird behavior or rejections when multiple clones are interacting with the same thing. Not yet tested on dedicated servers.

## **Roadmap**

*   Better upgrade system
*   ~~Redstone support~~
*   Improved editor
*   Improved visuals (most are placeholders)
*   More interactions and controls
*   Backport to 26.1, 1.21.x, and 1.20.1 (maybe)
*   ~~Fabric support~~
*   Third-party mod integrations

> **Work in progress:** This is a powerful mod, and so the bugs can be powerful too. Make a backup of your world if you try to push the limits. Expect bugs and compatibility issues until they are found and fixed. If you find something that is not working as expected or you have a suggestion, please leave an [issue](https://curseforge.com/minecraft/mc-mods/chronoclones/issues).
## **Development**

The repository is a [Stonecutter](https://stonecutter.kikugie.dev/) tree: one branch, one shared
source tree, one Gradle node per Minecraft-version-and-loader pair (currently `26.2-neoforge`
and `26.2-fabric`). Switch the active target with
`./gradlew "Set active project to <node>"`, run it with `./gradlew runActiveClient`, and build
everything with `./gradlew chiseledBuild`. See [docs/PORTING.md](docs/PORTING.md) for the full
workflow and what enabling the 1.21.1 / 1.20.1 targets involves.
