package gradle.logger.helper.plugin
import org.objectweb.asm.Type

object LoggerDefinitions {

    interface LoggerDefinition {
        fun loggerClassName(): String
        fun loggerInternalName(): String = loggerClassName().replace('.', '/')
        fun isLogMethod(methodName: String, methodDescriptor: String): Boolean
        fun correspondingLogSelectorMethodDescriptor(methodName:String, originalMethodDescriptor:String): String
        fun correspondingLogSelectorMethodName(methodName:String, originalMethodDescriptor:String): String
        fun isInterface(): Boolean
    }

    abstract class CommonsLoggingCompatibleLoggerDefinition : LoggerDefinition {
        val logMethodsToSelector = mapOf(Pair("trace", "isTraceEnabled"),
            Pair("debug", "isDebugEnabled"),
            Pair("info", "isInfoEnabled"),
            Pair("warn", "isWarnEnabled"),
            Pair("error", "isErrorEnabled")
        )
        override fun isInterface(): Boolean = true
        override fun isLogMethod(methodName: String, methodDescriptor: String) = methodName in logMethodsToSelector.keys


        override fun correspondingLogSelectorMethodName(methodName:String, originalMethodDescriptor:String): String = logMethodsToSelector.get(methodName)!!

        override fun correspondingLogSelectorMethodDescriptor(methodName:String, originalMethodDescriptor:String): String = Type.getMethodDescriptor(Type.BOOLEAN_TYPE)
    }

    object SLF4JLoggerDefinition : CommonsLoggingCompatibleLoggerDefinition() {
        override fun loggerClassName(): String = "org.slf4j.Logger"

    }



}