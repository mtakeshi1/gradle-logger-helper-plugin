package gradle.logger.helper.plugin

import org.objectweb.asm.MethodVisitor

fun interface DelayedInstruction {
    fun replay(visitor: MethodVisitor, logMethod: Boolean): Unit
}