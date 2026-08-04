@file:OptIn(dev.kikugie.stonecutter.StonecutterExperimentalAPI::class)

plugins {
    id("dev.kikugie.stonecutter")
    id("net.neoforged.moddev") version "2.0.143" apply false
}

stonecutter active file(".sc_active_version")

tasks.register("runActiveClient") {
    group = "stonecutter"
    description = "Run client of the active Stonecutter version"
    dependsOn(stonecutter.current!!.project + ":runClient")
}

tasks.register("runActiveServer") {
    group = "stonecutter"
    description = "Run server of the active Stonecutter version"
    dependsOn(stonecutter.current!!.project + ":runServer")
}

tasks.register("runActiveGameTests") {
    group = "stonecutter"
    description = "Run the gametest suite of the active Stonecutter version"
    dependsOn(stonecutter.current!!.project + ":runGameTestServer")
}

stonecutter parameters {
    // The loader half of the node name becomes the preprocessor constants:
    // //? if neoforge { ... //?} else { ... }
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge", "forge")
    swaps["minecraft"] = "\"${current.version}\";"
}
