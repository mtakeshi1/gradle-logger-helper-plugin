defaultTasks("buildAll")

tasks.register("buildAll") {
    dependsOn(gradle.includedBuild("pluginroot").task(":plugin:assemble"))
    dependsOn(gradle.includedBuild("plugintestmodule").task(":app:assemble"))

}

tasks.register("checkAll") {
    dependsOn(gradle.includedBuild("pluginroot").task(":plugin:check"))
    dependsOn(gradle.includedBuild("plugintestmodule").task(":app:check"))
}
