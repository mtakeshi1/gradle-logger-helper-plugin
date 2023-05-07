package gradle.logger.helper.plugin

fun interface LogStringModifier {

    fun apply(constant: String, packagetName: String, className: String, methodName: String, lineNumber: Int): String

}