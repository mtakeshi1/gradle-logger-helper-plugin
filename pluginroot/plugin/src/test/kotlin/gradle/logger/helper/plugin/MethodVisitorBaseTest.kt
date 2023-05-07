package gradle.logger.helper.plugin

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.util.CheckClassAdapter
import org.objectweb.asm.util.TraceClassVisitor
import org.slf4j.Logger
import java.io.PrintWriter

open class MethodVisitorBaseTest {

    interface SL4JLoggerAware {
        fun setLogger(logger: Logger)
    }

    class OpenClassLoader(parent: ClassLoader): ClassLoader(parent) {
        fun forceDefineClass(name: String, b: ByteArray): Class<*> {
            return super.defineClass(name, b, 0, b.size)!!
        }

    }

    fun modifyClassCreateInstance(baseType: Class<*>): Any {
        return generateClass(baseType).getConstructor().newInstance()
    }

    private fun <A> generateClass(baseType: Class<A>): Class<*> {
        val reader = ClassReader(baseType.classLoader.getResourceAsStream(baseType.name.replace('.', '/') + ".class"))
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val internalName: String = Type.getInternalName(baseType)
        val newInternalName = "$internalName\$LOGGER_${System.nanoTime()}"
        val newClassName = newInternalName.replace('/', '.')
        val mapOf = mapOf(Pair(internalName, newInternalName))
        val tracer = TraceClassVisitor(cw, PrintWriter(System.out))
        val checker = CheckClassAdapter(tracer, true)

        val remapper = ClassRemapper(checker, SimpleRemapper(mapOf))
        val f =
            LogStringModifier { constant: String, packagetName: String, className: String, methodName: String, lineNumber: Int -> constant }
        val loggerVisitor = LoggerClassVisitor(remapper, f, LoggerDefinitions.SLF4JLoggerDefinition)
        reader.accept(loggerVisitor, ClassReader.EXPAND_FRAMES)
        val loader = OpenClassLoader(baseType.classLoader)
        return loader.forceDefineClass(newClassName, cw.toByteArray())
    }

    fun <A : SL4JLoggerAware> modifyClassInjectLogger(baseType: Class<A>, enabled: Map<String, Boolean>, logOutput: MutableMap<String, MutableList<Array<Any>>>): SL4JLoggerAware {
        val instance = modifyClassCreateInstance(baseType) as SL4JLoggerAware
        val logger: Logger = LoggerProxyFactory.newMonitoringInstance(
            Logger::class.java,
            LoggerDefinitions.SLF4JLoggerDefinition,
            enabled, logOutput
        )
        instance.setLogger(logger)
        return instance
    }

}