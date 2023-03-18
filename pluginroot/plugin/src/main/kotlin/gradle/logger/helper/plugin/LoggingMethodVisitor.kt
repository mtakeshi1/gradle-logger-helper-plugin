package com.cittati.logger

import org.gradle.api.logging.Logger
import org.objectweb.asm.*


interface LogLevel {
    val methodName: String
}

enum class CommonsLoggingLogLevel(override val methodName: String) : LogLevel {
    FATAL("isFatalEnabled"), ERROR("isErrorEnabled"), WARN("isWarnEnabled"),
    INFO("isInfoEnabled"), DEBUG("isDebugEnabled"), TRACE("isTraceEnabled")
}


class LoggingMethodVisitor(
    private val methodName: String,
    delegate: MethodVisitor,
    log: Logger,
    methodDescriptor: String,
    modifiers: Int,
    loggingClassName: String
) :
    MethodVisitor(Opcodes.ASM7, delegate) {
    private val log: Logger
    private val instructions: MutableList<RecordedInstruction> = ArrayList()
    private var lineNumber = 0
    private var level: LogLevel? = null
    private var skipJump: Label? = null
    private var logAccessCount = 0
    private val localVariableTypeDescriptors: MutableMap<Int, String> = HashMap()
    private val loggerClassInternalName: String
    private val loggerClassTypeDescriptor: String

    init {
        this.log = log
        loggerClassInternalName = loggingClassName.replace('.', '/')
        loggerClassTypeDescriptor = "L$loggerClassInternalName;"
        log.debug("Starting method: $methodName")
        val argumentTypes = Type.getArgumentTypes(methodDescriptor)
        var offset = 0
        if (modifiers and Opcodes.ACC_STATIC == 0) {
            offset++
            localVariableTypeDescriptors[0] = "this"
        }
        for (argType in argumentTypes) {
            localVariableTypeDescriptors[offset] = argType.descriptor
            offset += argType.size
//            if (argType == Type.DOUBLE_TYPE || argType == Type.LONG_TYPE) {
//                offset++
//            }
        }
    }

    @Deprecated("")
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        val recordedInstruction = RecordedInstruction { it.visitMethodInsn(opcode, owner, name, descriptor) }
        maybeLoggerCall(owner, name, recordedInstruction, descriptor)
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        //super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        val recordedInstruction =
            RecordedInstruction { it.visitMethodInsn(opcode, owner, name, descriptor, isInterface) }
        maybeLoggerCall(owner, name, recordedInstruction, descriptor)
    }

    private fun maybeLoggerCall(
        owner: String,
        name: String,
        recordedInstruction: RecordedInstruction,
        descriptor: String
    ) {
        if (loggerClassInternalName == owner) {
            log.debug("found log method call: '$name' at: $methodName:$lineNumber")
            val level: LogLevel? = detectLogLevel(name)
            if (level != null) {
                this.level = level
                log.debug("found log level: '$level' at: $methodName:$lineNumber. Playing back instructions")
                playbackInstructions()
                if (skipJump == null) {
//                    throw new RuntimeException("could not find skip jump label on method: " + methodName + " close to line: " + lineNumber);
                    recordedInstruction.playback(super.mv)
                    log.debug("Jump instruction not found. The log is probably on a local variable and hence we will not record it")
                    skipJump = null
                    this.level = null
                    return
                }
                val skipEverything = Label()
                recordedInstruction.playback(super.mv)
                super.visitJumpInsn(Opcodes.GOTO, skipEverything)
                super.visitLabel(skipJump)
                super.visitInsn(Opcodes.POP)
                super.visitLabel(skipEverything)
                log.debug("Jump set. Reseting label and log level")
                skipJump = null
                this.level = null
            } else {
                log.debug("did not find log level:  at: $methodName:$lineNumber. Playing back instructions without log level")
                instructions.add(recordedInstruction)
            }
        } else if (Type.getReturnType(descriptor) == Type.getObjectType(loggerClassInternalName)) {
            instructions.add(recordedInstruction)
            instructions.add(LogAccessInstruction())
        } else {
            instructions.add(recordedInstruction)
        }
    }

    private fun detectLogLevel(name: String): LogLevel? {
        return when (name) {
            "trace" -> CommonsLoggingLogLevel.TRACE
            "debug" -> CommonsLoggingLogLevel.DEBUG
            "info" -> CommonsLoggingLogLevel.INFO
            "warn" -> CommonsLoggingLogLevel.WARN
            "error" -> CommonsLoggingLogLevel.ERROR
            "fatal" -> CommonsLoggingLogLevel.FATAL
            else -> null
        }
    }

    fun interface RecordedInstruction {
        fun playback(visitor: MethodVisitor)
    }

    override fun visitInsn(opcode: Int) {
        instructions.add(RecordedInstruction { it.visitInsn(opcode) })
    }

    override fun visitVarInsn(opcode: Int, `var`: Int) {
        instructions.add(RecordedInstruction { it.visitVarInsn(opcode, `var`) })
        if (opcode == Opcodes.ALOAD && loggerClassTypeDescriptor == localVariableTypeDescriptors[`var`]) { // I actually know
            instructions.add(LogAccessInstruction())
        }
    }

    override fun visitTypeInsn(opcode: Int, type: String) {
        instructions.add(RecordedInstruction { it.visitTypeInsn(opcode, type) })
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        instructions.add(RecordedInstruction { it.visitFieldInsn(opcode, owner, name, descriptor) })
        if ((opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) && loggerClassTypeDescriptor == descriptor) {
            instructions.add(LogAccessInstruction())
        }
    }

    override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any
    ) {
        instructions.add(RecordedInstruction {
            it.visitInvokeDynamicInsn(
                name, descriptor,
                bootstrapMethodHandle, *bootstrapMethodArguments
            )
        })
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        instructions.add(RecordedInstruction { it.visitJumpInsn(opcode, label) })
    }

    override fun visitLabel(label: Label) {
        instructions.add(RecordedInstruction { it.visitLabel(label) })
    }

    override fun visitLdcInsn(value: Any) {
        instructions.add(RecordedInstruction { it.visitLdcInsn(value) })
    }

    override fun visitIincInsn(variableIndex: Int, increment: Int) {
        instructions.add(RecordedInstruction { it.visitIincInsn(variableIndex, increment) })
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
        instructions.add(RecordedInstruction { it.visitTableSwitchInsn(min, max, dflt, *labels) })
    }

    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<Label>) {
        instructions.add(RecordedInstruction { it.visitLookupSwitchInsn(dflt, keys, labels) })
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        instructions.add(RecordedInstruction { it.visitMultiANewArrayInsn(descriptor, numDimensions) })
    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String) {
        instructions.add(RecordedInstruction { it.visitTryCatchBlock(start, end, handler, type) })
    }

    override fun visitLocalVariable(
        name: String,
        descriptor: String,
        signature: String,
        start: Label,
        end: Label,
        index: Int
    ) {
        instructions.add(RecordedInstruction { it.visitLocalVariable(name, descriptor, signature, start, end, index) })
    }

    override fun visitLineNumber(line: Int, start: Label) {
        instructions.add(RecordedInstruction {
            it.visitLineNumber(line, start)
            lineNumber = line
        })
    }

    override fun visitParameter(name: String, access: Int) {
        instructions.add(RecordedInstruction { it.visitParameter(name, access) })
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        instructions.add(RecordedInstruction { it.visitIntInsn(opcode, operand) })
    }

    override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
        instructions.add(RecordedInstruction { it.visitAnnotableParameterCount(parameterCount, visible) })
    }

    override fun visitAttribute(attribute: Attribute) {
        instructions.add(RecordedInstruction { it.visitAttribute(attribute) })
    }

    override fun visitCode() {
        instructions.add(RecordedInstruction { it.visitCode() })
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<Any>, numStack: Int, stack: Array<Any>) {
        instructions.add(RecordedInstruction { it.visitFrame(type, numLocal, local, numStack, stack) })
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        instructions.add(RecordedInstruction { it.visitMaxs(maxStack, maxLocals) })
    }

    override fun visitTryCatchAnnotation(
        typeRef: Int,
        typePath: TypePath,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor {
        playbackInstructions()
        return super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        playbackInstructions()
        return super.visitAnnotation(descriptor, visible)
    }

    override fun visitAnnotationDefault(): AnnotationVisitor {
        playbackInstructions()
        return super.visitAnnotationDefault()
    }

    override fun visitInsnAnnotation(
        typeRef: Int,
        typePath: TypePath,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor {
        playbackInstructions()
        return super.visitInsnAnnotation(typeRef, typePath, descriptor, visible)
    }

    override fun visitLocalVariableAnnotation(
        typeRef: Int,
        typePath: TypePath,
        start: Array<Label>,
        end: Array<Label>,
        index: IntArray,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor {
        playbackInstructions()
        return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible)
    }

    override fun visitParameterAnnotation(parameter: Int, descriptor: String, visible: Boolean): AnnotationVisitor {
        playbackInstructions()
        return super.visitParameterAnnotation(parameter, descriptor, visible)
    }

    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath,
        descriptor: String,
        visible: Boolean
    ): AnnotationVisitor {
        playbackInstructions()
        return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
    }

    override fun visitEnd() {
        playbackInstructions()
        super.visitEnd()
    }

    private fun playbackInstructions() {
        log.debug("Playing back instructions: " + instructions.size)
        val iterator = instructions.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            next.playback(super.mv)
            iterator.remove()
        }
    }

    private inner class LogAccessInstruction() : RecordedInstruction {
        init {
            logAccessCount++
        }

        override fun playback(visitor: MethodVisitor) {
            log.debug("Found access to Log at: $methodName:$lineNumber with $logAccessCount remaining")
            if (--logAccessCount == 0 && level != null) {
                log.debug("Dupping apache logger at: $methodName:$lineNumber")
                if (skipJump != null) {
                    throw RuntimeException("Wrong state. Expected skip jump to be null at method: $methodName. Last know line: $lineNumber")
                }
                skipJump = Label()
                visitor.visitInsn(Opcodes.DUP)
                visitor.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE, loggerClassInternalName, level!!.methodName, Type.getMethodDescriptor(
                        Type.BOOLEAN_TYPE
                    ), true
                )
                visitor.visitJumpInsn(Opcodes.IFEQ, skipJump)
            } else {
                log.debug("Found apache logger at $methodName:$lineNumber but no level detected. Skipping")
            }
        }
    }
}