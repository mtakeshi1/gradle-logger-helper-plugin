package gradle.logger.helper.plugin

interface LogTester {

    fun debug(msg: String)
    fun debugWithLine(msg: String) = debug("{lineNumber} " + msg)

}