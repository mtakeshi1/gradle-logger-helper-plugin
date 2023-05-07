package gradle.logger.helper.plugin

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AnalyzerAdapter

class LoggerClassVisitor(
    delegate: ClassVisitor,
    private val stringAdaptor: LogStringModifier,
    private val loggerDefinition: LoggerDefinitions.LoggerDefinition
) : ClassVisitor(Opcodes.ASM9, delegate) {

    private lateinit var ownerInternalName: String

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        ownerInternalName = name!!
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
        val stack = AnalyzerAdapter(ownerInternalName, access, name!!, descriptor ?: "", delegate)
        return LoggerMethodVisitor(ownerInternalName, access, name, descriptor ?: "", stack, stringAdaptor, loggerDefinition)
    }


}