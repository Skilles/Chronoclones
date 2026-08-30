plugins {
    `java-library`
    id("dev.kikugie.loom-back-compat")
}

stonecutter {
    // Must mirror build.neoforge.gradle.kts exactly: replacements rewrite the shared source
    // in place, so every node has to agree on the rename table.
    replacements.string(current.parsed < "26") {
        replace("net.minecraft.client.renderer.rendertype.RenderTypes", "net.minecraft.client.renderer.RenderType")
        replace("RenderTypes.", "RenderType.")
        replace("GuiGraphicsExtractor", "GuiGraphics")
        replace("Identifier", "ResourceLocation")
        replace("ContainerInput", "ClickType")
        replace("EntitySpawnReason.", "MobSpawnType.")
        replace("centeredText(", "drawCenteredString(")
        replace(".text(font", ".drawString(font")
        replace("fakeItem(", "renderFakeItem(")
        replace("setScreenAndShow(", "setScreen(")
        replace("net.minecraft.world.entity.player.PlayerSkin", "net.minecraft.client.resources.PlayerSkin")
        replace("extractContents(", "renderWidget(")
        replace("extractLabels(", "renderLabels(")
        replace("getGameProfile().name()", "getGameProfile().getName()")
        replace("stack.typeHolder(), stack.getComponentsPatch()", "stack.getItemHolder(), stack.getComponentsPatch()")
        replace("snapTo(", "moveTo(")
        replace("PayloadTypeRegistry.serverboundPlay()", "PayloadTypeRegistry.playC2S()")
        replace("PayloadTypeRegistry.clientboundPlay()", "PayloadTypeRegistry.playS2C()")
        replace("ContainerStorage", "InventoryStorage")
        replace("START_LEVEL_TICK", "START_WORLD_TICK")
        replace("END_LEVEL_TICK", "END_WORLD_TICK")
        replace("ServerEntityLevelChangeEvents", "ServerEntityWorldChangeEvents")
        replace("AFTER_PLAYER_CHANGE_LEVEL", "AFTER_PLAYER_CHANGE_WORLD")
        replace("net.fabricmc.fabric.api.menu.v1.ExtendedMenuType", "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType")
        replace("net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider", "net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory")
    }

    // Everything below 1.20.5 predates stream codecs, payload types and registry buffers;
    // the mod-owned compat package stands in, wired by switching the imports.
    replacements.string(current.parsed < "1.20.5") {
        replace("net.minecraft.network.codec.StreamCodec", "com.skilles.chronoclones.compat.StreamCodec")
        replace("net.minecraft.network.codec.ByteBufCodecs", "com.skilles.chronoclones.compat.ByteBufCodecs")
        replace("net.minecraft.network.RegistryFriendlyByteBuf", "com.skilles.chronoclones.compat.RegistryFriendlyByteBuf")
        replace("net.minecraft.network.protocol.common.custom.CustomPacketPayload", "com.skilles.chronoclones.compat.CustomPacketPayload")
        replace("ItemStack.OPTIONAL_STREAM_CODEC", "com.skilles.chronoclones.compat.MojCodecs.OPTIONAL_ITEM_STACK")
        replace("ItemStack.STREAM_CODEC", "com.skilles.chronoclones.compat.MojCodecs.ITEM_STACK")
        replace("BlockPos.STREAM_CODEC", "com.skilles.chronoclones.compat.MojCodecs.BLOCK_POS")
        replace("Direction.STREAM_CODEC", "com.skilles.chronoclones.compat.MojCodecs.DIRECTION")
        replace("UUIDUtil.STREAM_CODEC", "com.skilles.chronoclones.compat.MojCodecs.UUID_STREAM")
        replace("registries.createSerializationContext(JsonOps.INSTANCE)", "net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE, registries)")
        replace("registries.createSerializationContext(NbtOps.INSTANCE)", "net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, registries)")
        replace(".getOrThrow()", ".getOrThrow(false, error -> {})")
        replace(".getOrThrow(msg -> new AssertionError(msg))", ".getOrThrow(false, msg -> {})")
        replace(".getOrThrow(msg -> new AssertionError(\"codec failed: \" + msg))", ".getOrThrow(false, msg -> new AssertionError(msg))")
    }

    // PlayerSkin arrived in 1.20.2; older nodes use the mod's lookalike record.
    replacements.string(current.parsed < "1.20.2") {
        replace("net.minecraft.client.resources.PlayerSkin", "com.skilles.chronoclones.compat.PlayerSkin")
    }
}

val modId: String = property("mod_id") as String
val minecraftVersion: String = property("minecraft_version") as String
val fabricLoaderVersion: String = property("fabric_loader_version") as String
val fabricApiVersion: String = property("fabric_api_version") as String
val releaseChannel: String = providers.gradleProperty("release_channel").map { it.trim() }.getOrElse("")

version = property("mod_version") as String
group = property("mod_group_id") as String

base {
    archivesName = "$modId-$minecraftVersion-fabric"
}

// On 26.x the unobfuscated era has nothing to remap, so the plain jar is the artifact.
// On pre-26 nodes loom shunts the plain jar to devlibs and remapJar's output ships instead.
tasks.named<Jar>("jar") {
    archiveClassifier.set(if (stonecutter.current.parsed < "26") "dev" else releaseChannel)
}

// Mojang ships Java 25 to end users in 26.2, so mods should target Java 25.
java.toolchain.languageVersion = JavaLanguageVersion.of(25)

// Older eras ship on older stock Java (21 for 1.21.1, 17 for 1.20.1). The source stays on the
// 25 toolchain; JvmDowngrader lowers the intermediary-remapped jar's bytecode (pattern
// switches and all) and shades stubs for the newer APIs (Math.clamp, List.getFirst, ...).
if (stonecutter.current.parsed < "26") {
    apply(plugin = "xyz.wagyourtail.jvmdowngrader")

    extensions.configure<xyz.wagyourtail.jvmdg.gradle.JVMDowngraderExtension> {
        downgradeTo.set(if (stonecutter.current.parsed < "1.20.5") JavaVersion.VERSION_17 else JavaVersion.VERSION_21)
    }

    // The remapped-but-not-downgraded jar steps aside as "-dev".
    val remapJar = tasks.named<org.gradle.jvm.tasks.Jar>("remapJar") {
        archiveClassifier.set("dev")
    }

    tasks.named<xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar>("downgradeJar") {
        inputFile.set(remapJar.flatMap { it.archiveFile })
        classpath = sourceSets["main"].compileClasspath
    }

    // The shaded jar takes the canonical artifact name the remapped jar vacated.
    tasks.named<xyz.wagyourtail.jvmdg.gradle.task.ShadeJar>("shadeDowngradedApi") {
        archiveClassifier.set(releaseChannel)
    }

    tasks.named("assemble") {
        dependsOn("shadeDowngradedApi")
    }
}

sourceSets {
    main {
        resources {
            // Include resources generated by data generators (run from the neoforge node).
            srcDir(rootProject.file("src/generated/resources"))

            exclude("**/*.bbmodel")
            exclude("**/.cache")
        }
    }

    // Game tests need a running server and must not ship: they register ids into real registries.
    // Stonecutter wires every source set's directories itself; only the classpath needs linking.
    create("gametest") {
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    }
}

val accessWidenerFile = rootProject.file("src/main/resources/aw/${stonecutter.current.version}.accesswidener")

loom {
    accessWidenerPath = accessWidenerFile

    mods {
        create(modId) {
            sourceSet(sourceSets["main"])
        }
        // Its own dev mod: it carries a second fabric.mod.json whose entrypoint registers the
        // test functions, which the loader would ignore inside the main mod's group.
        create("chronoclones_gametest") {
            sourceSet(sourceSets["gametest"])
        }
    }

    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            runDir = "run/client"
        }
        named("server") {
            server()
            configName = "Fabric Server"
            runDir = "run/server"
        }
        create("gametest") {
            server()
            configName = "Fabric GameTest Server"
            runDir = "run/gametest"
            vmArg("-Dfabric-api.gametest")
            source(sourceSets["gametest"])
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
    "gametestCompileOnly"("org.jspecify:jspecify:1.0.0")
    testCompileOnly("org.jspecify:jspecify:1.0.0")

    minecraft("com.mojang:minecraft:$minecraftVersion")
    // 26.x is the unobfuscated era and needs no mappings; older targets develop against mojmap.
    if (stonecutter.current.parsed < "26") {
        mappings(loom.layered { officialMojangMappings() })
    }
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Boots the loader inside JUnit so registries exist when tests touch game objects.
    testImplementation("net.fabricmc:fabric-loader-junit:$fabricLoaderVersion")
}

tasks.test {
    useJUnitPlatform()
    // Picks up GameBootstrapExtension, which boots the vanilla registries before test classes.
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
}

val replaceProperties = mapOf(
    "minecraft_version" to minecraftVersion,
    "minecraft_version_range" to property("minecraft_version_range") as String,
    "fabric_loader_version" to fabricLoaderVersion,
    "mod_id" to modId,
    "mod_name" to property("mod_name") as String,
    "mod_license" to property("mod_license") as String,
    "mod_version" to property("mod_version") as String,
)

tasks.withType<ProcessResources>().configureEach {
    inputs.properties(replaceProperties)

    // Mixin before 0.8.7 does not know JAVA_25; the mixin classes themselves are fine.
    if (stonecutter.current.parsed < "26") {
        filesMatching("*.mixins.json") {
            filter { line -> line.replace("\"compatibilityLevel\": \"JAVA_25\"", "\"compatibilityLevel\": \"JAVA_21\"") }
        }
    }

    // assets/<ns>/items item-model definitions are a 1.21.4+ format; equipment assets are 1.21.2+.
    if (stonecutter.current.parsed < "1.21.2") {
        exclude("assets/*/items/**")
        exclude("assets/*/equipment/**")
    }


    // Pre-1.20.5 data packs use the plural folder names and item-keyed recipe results;
    // the dropped anchor's loot function becomes its copy_nbt ancestor.
    if (stonecutter.current.parsed < "1.20.5") {
        filesMatching("data/**") {
            path = path
                .replace(Regex("data/([^/]+)/advancement/"), "data/$1/advancements/")
                .replace(Regex("data/([^/]+)/loot_table/"), "data/$1/loot_tables/")
                .replace(Regex("data/([^/]+)/recipe/"), "data/$1/recipes/")
                .replace(Regex("data/([^/]+)/structure/"), "data/$1/structures/")
                .replace(Regex("data/([^/]+)/tags/block/"), "data/$1/tags/blocks/")
                .replace(Regex("data/([^/]+)/tags/item/"), "data/$1/tags/items/")
        }
        filesMatching("data/*/recipe/*.json") {
            filter { line -> line.replace("\"id\": \"", "\"item\": \"") }
        }
        exclude("data/*/loot_table/blocks/chrono_anchor.json")
        from(rootProject.file("src/main/resources/compat1201/chrono_anchor_loot.json")) {
            into("data/chronoclones/loot_tables/blocks")
            rename { "chrono_anchor.json" }
        }
    }
    exclude("compat1201/**")

    // Plain-string ingredients arrived in 1.21.2; older parsers want the {"item": id} object.
    if (stonecutter.current.parsed < "1.21.2") {
        filesMatching("data/*/recipe/*.json") {
            filter { line -> line.replace(Regex("\"([A-Z])\": \"([a-z0-9_.:/-]+)\""), "\"$1\": { \"item\": \"$2\" }") }
        }
    }

    filesMatching(listOf("fabric.mod.json")) {
        expand(replaceProperties)
    }

    // Pre-26 has no test-function registry: the annotated shims register through the
    // fabric-gametest entrypoint, added here so the 26.x json stays free of a missing class.
    if (stonecutter.current.parsed < "26") {
        filesMatching("fabric.mod.json") {
            filter { line -> line.replace(
                "\"com.skilles.chronoclones.gametest.FabricGametestInit\"",
                "\"com.skilles.chronoclones.gametest.FabricGametestInit\" ], " +
                        "\"fabric-gametest\": [ \"com.skilles.chronoclones.gametest.GeneratedGameTests\"") }
        }
    }

    // The shipped jar is downgraded to the era's stock Java; the dependency gate follows it.
    if (stonecutter.current.parsed < "26") {
        val javaRequirement = if (stonecutter.current.parsed < "1.20.5") ">=17" else ">=21"
        filesMatching("fabric.mod.json") {
            filter { line -> line.replace("\"java\": \">=25\"", "\"java\": \"$javaRequirement\"") }
        }
    }

    // The NeoForge half of the metadata never ships in a fabric jar.
    exclude("META-INF/neoforge.mods.toml")
    exclude("META-INF/accesstransformer.cfg")
    exclude("at/**")

    // The per-version AW ships at the path fabric.mod.json names; the sources stay out.
    exclude("aw/**")
    from(accessWidenerFile) {
        rename { "chronoclones.accesswidener" }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
