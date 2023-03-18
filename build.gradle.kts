defaultTasks("buildAll")

tasks.register("buildAll") {
    dependsOn(gradle.includedBuild("pluginroot").task(":plugin:assemble"))
    dependsOn(gradle.includedBuild("plugintestmodule").task(":app:assemble"))
}

tasks.register("cleanAll") {
    dependsOn(gradle.includedBuild("pluginroot").task(":plugin:clean"))
    dependsOn(gradle.includedBuild("plugintestmodule").task(":app:clean"))
}


tasks.register("checkAll") {
    dependsOn(gradle.includedBuild("pluginroot").task(":plugin:check"))
    dependsOn(gradle.includedBuild("plugintestmodule").task(":app:check"))
}

tasks.register("buildAndRun") {
    dependsOn(gradle.includedBuild("pluginroot").task(":plugin:clean"))
    dependsOn(gradle.includedBuild("plugintestmodule").task(":app:clean"))
    dependsOn(gradle.includedBuild("pluginroot").task(":plugin:assemble"))
    dependsOn(gradle.includedBuild("plugintestmodule").task(":app:assemble"))
}
