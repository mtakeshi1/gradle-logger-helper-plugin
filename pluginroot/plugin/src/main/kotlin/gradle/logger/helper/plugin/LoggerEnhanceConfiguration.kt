package gradle.logger.helper.plugin

import org.gradle.api.provider.Property

abstract class LoggerEnhanceConfiguration {
    abstract fun getPackageNamePattern(): Property<String>
    abstract fun getClassNamePattern(): Property<String>
    abstract fun getMethodNamePattern(): Property<String>
    abstract fun getLineNumberPattern(): Property<String>
}
