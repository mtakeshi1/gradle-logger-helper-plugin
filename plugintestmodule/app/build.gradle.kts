plugins {
    `java-library`
    id("gradle.logger.helper.plugin.greeting")
}

group = "io.githut.mtakeshi"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-logging:commons-logging:1.2")
    implementation("org.slf4j:slf4j-api:2.0.6")

}

enhanceLogger {
    getLineNumberPattern().set("__line_number__")
}