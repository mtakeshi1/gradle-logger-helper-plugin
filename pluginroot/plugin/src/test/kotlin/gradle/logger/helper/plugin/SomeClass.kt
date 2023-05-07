package gradle.logger.helper.plugin

import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.ASMifier
import org.objectweb.asm.util.TraceClassVisitor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter

class SomeClass : MethodVisitorBaseTest.SL4JLoggerAware, LogTester {

    private lateinit var logger: Logger

    object SomeClass {
        val LOGGER: Logger = LoggerFactory.getLogger(SomeClass::class.java)
    }

    fun shielded(a: String) {
        if (SomeClass.LOGGER.isDebugEnabled) {
            SomeClass.LOGGER.debug(Utils.preffix + " -> something" + a)
        }
    }

    override fun setLogger(logger: Logger) {
        this.logger = logger
    }

    override fun debug(msg: String) {
        logger.debug(msg)
    }

}

fun main() {
    val baseType = SomeClass::class.java
    var reader = ClassReader(baseType.classLoader.getResourceAsStream(baseType.name.replace('.', '/') + ".class"))
    val tracer = TraceClassVisitor(null, ASMifier(), PrintWriter(System.out))
    reader.accept(tracer, ClassReader.EXPAND_FRAMES)
    reader = ClassReader(baseType.classLoader.getResourceAsStream(baseType.name.replace('.', '/') + ".class"))
    reader.accept(TraceClassVisitor(PrintWriter(System.out)), ClassReader.EXPAND_FRAMES)
}