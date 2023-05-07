package gradle.logger.helper.plugin

import org.gradle.api.logging.Logger
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object LoggerProxyFactory {

    fun newInstance(): Logger {
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Logger::class.java)) { _: Any, method: Method, _: Array<Any> ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                String::class.java -> "name"
                else -> 1
            }
        } as Logger
    }

    fun <A> newMonitoringInstance(type: Class<A>, loggerDefinition: LoggerDefinitions.CommonsLoggingCompatibleLoggerDefinition, enabled: Map<String, Boolean>, logOutput: MutableMap<String, MutableList<Array<Any>>>): A {
        val countMap: MutableMap<String, Int> = mutableMapOf()
        val invHandler = InvocationHandler{ _: Any, method: Method, args: Array<Any> ->
            for (x in loggerDefinition.logMethodsToSelector.entries) {
                if(x.key == method.name) {
                    val filterCount  = countMap.getOrDefault(method.name, 0)
                    countMap[method.name] = filterCount - 1
                    if(enabled.getOrDefault(x.key, false)) {
                        val old =  logOutput.getOrDefault(x.key, mutableListOf())
                        old.add(args)
                        logOutput[x.key] = old
                    }
                    break
                } else if(x.value == method.name) {
                    countMap[method.name] = countMap.getOrDefault(method.name, 0) + 1
                    return@InvocationHandler enabled.getOrDefault(method.name, false)
                }
            }
            when(method.returnType) {
                java.lang.Boolean.TYPE -> false
                else -> null
            }
        }
        return type.cast(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type), invHandler))
    }


}