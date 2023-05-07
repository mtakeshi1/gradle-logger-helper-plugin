package gradle.logger.helper.plugin

import org.slf4j.Logger

class MinimalClass {

    private lateinit var logger: Logger

    fun debug() {
        logger.debug("some debug message")
    }
}