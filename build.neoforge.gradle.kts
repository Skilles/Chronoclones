plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev")
}

stonecutter {
    // Canonical source is written in 26.x names; older targets read the mojmap-era names.
    // Tokens are chosen to be collision-free (checked against the codebase); order matters.
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
val neoVersion: String = property("neo_version") as String
val releaseChannel: String = providers.gradleProperty("release_channel").map { it.trim() }.getOrElse("")

version = property("mod_version") as String
group = property("mod_group_id") as String

base {
    archivesName = "$modId-$minecraftVersion-neoforge"
}

// On pre-26 nodes the plain jar steps aside as "-dev"; the downgraded jar takes its name below.
tasks.named<Jar>("jar") {
    archiveClassifier.set(if (stonecutter.current.parsed < "26") "dev" else releaseChannel)
}

// Mojang ships Java 25 to end users in 26.2, so mods should target Java 25.
java.toolchain.languageVersion = JavaLanguageVersion.of(25)

// Older eras ship on older stock Java (21 for 1.21.1). The source stays on the 25 toolchain;
// JvmDowngrader lowers the built jar's bytecode and shades stubs for the newer APIs.
if (stonecutter.current.parsed < "26") {
    apply(plugin = "xyz.wagyourtail.jvmdowngrader")

    extensions.configure<xyz.wagyourtail.jvmdg.gradle.JVMDowngraderExtension> {
        downgradeTo.set(if (stonecutter.current.parsed < "1.20.5") JavaVersion.VERSION_17 else JavaVersion.VERSION_21)
    }

    tasks.named<xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar>("downgradeJar") {
        classpath = sourceSets["main"].compileClasspath
    }

    // The shaded jar takes the canonical artifact name the plain jar vacated. The stub
    // package must be an explicit valid Java package: the default derives it from the
    // archive name, whose hyphens FML's module scanner rejects ("not a Java identifier").
    tasks.named<xyz.wagyourtail.jvmdg.gradle.task.ShadeJar>("shadeDowngradedApi") {
        archiveClassifier.set(releaseChannel)
        shadePath.set("com/skilles/chronoclones/jvmdg/")
    }

    tasks.named("assemble") {
        dependsOn("shadeDowngradedApi")
    }
}

sourceSets {
    main {
        resources {
            // Include resources generated by data generators.
            srcDir(rootProject.file("src/generated/resources"))

            // Exclude common development only resources from finalized outputs
            exclude("**/*.bbmodel") // BlockBench project files
            exclude("**/.cache")    // datagen cache files
        }
    }

    // Game tests need a running server and must not ship: they register ids into real registries.
    // Stonecutter wires every source set's directories itself; only the classpath needs linking.
    create("gametest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations {
    named("gametestImplementation") { extendsFrom(configurations.implementation.get()) }
    named("gametestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }

    // 'localRuntime' declares runtime-testing-only dependencies that dependents never inherit.
    create("localRuntime")
    named("runtimeClasspath") { extendsFrom(configurations["localRuntime"]) }
}

repositories {
    mavenCentral()
}

val accessTransformerFile = rootProject.file("src/main/resources/at/${stonecutter.current.version}.cfg")

neoForge {
    version = neoVersion

    accessTransformers.from(accessTransformerFile)
    accessTransformers.publish(accessTransformerFile)

    validateAccessTransformers = true

    addModdingDependenciesTo(sourceSets["gametest"])

    mods {
        create(modId) {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["gametest"])
        }
    }

    val runsRoot = layout.projectDirectory

    runs {
        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }

        create("client") {
            client()
            devLogin = true
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            gameDirectory = runsRoot.dir("run/client")
        }

        create("server") {
            server()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            programArgument("--nogui")
            gameDirectory = runsRoot.dir("run/server")
        }

        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            gameDirectory = runsRoot.dir("run/gameTestServer")
            // -PgameTestFilter=chronoclones:some_test_* runs a subset in isolation;
            // -PgameTestRepeat=N reruns each selected test N times to shake out flakes.
            if (project.hasProperty("gameTestFilter")) {
                programArguments.addAll("--tests", project.property("gameTestFilter").toString())
            }
            if (project.hasProperty("gameTestRepeat")) {
                programArguments.addAll("--repeatCount", project.property("gameTestRepeat").toString())
            }
        }

        create("clientData") {
            clientData()
            programArguments.addAll("--mod", modId,
                    "--all",
                    "--output", rootProject.file("src/generated/resources/").absolutePath,
                    "--existing", rootProject.file("src/main/resources/").absolutePath)
            gameDirectory = runsRoot.dir("run/clientData")
        }
    }

    // Runs `test` through FML, or anything touching BuiltInRegistries has no loader.
    unitTest {
        enable()
        testedMod = mods.named(modId).get()
    }
}

dependencies {
    // 26.x NeoForge ships jspecify transitively; older targets need it named.
    compileOnly("org.jspecify:jspecify:1.0.0")
    "gametestCompileOnly"("org.jspecify:jspecify:1.0.0")
    testCompileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val replaceProperties = mapOf(
    "minecraft_version" to minecraftVersion,
    "minecraft_version_range" to property("minecraft_version_range") as String,
    "neo_version" to neoVersion,
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

    filesMatching(listOf("META-INF/neoforge.mods.toml")) {
        expand(replaceProperties)
    }

    // The Fabric half of the metadata never ships in a neoforge jar.
    exclude("fabric.mod.json")
    exclude("chronoclones.accesswidener")
    exclude("aw/**")
    exclude("chronoclones.fabric.mixins.json")

    // The per-version AT ships at the path neoforge.mods.toml names; the sources stay out.
    exclude("at/**")
    from(accessTransformerFile) {
        into("META-INF")
        rename { "accesstransformer.cfg" }
    }
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(rootProject.layout.projectDirectory.dir("repo"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
