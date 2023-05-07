package gradle.logger.helper.plugin

import org.objectweb.asm.*
import org.objectweb.asm.commons.AnalyzerAdapter
import org.objectweb.asm.commons.LocalVariablesSorter

class LoggerMethodVisitor(
    ownerInternalName: String,
    access: Int,
    private val methodName: String,
    descriptor: String,
    mvDelegate: MethodVisitor,
    private val stringAdaptor: LogStringModifier,
    private val loggerDefinition: LoggerDefinitions.LoggerDefinition
) : LocalVariablesSorter(Opcodes.ASM9, access, descriptor, mvDelegate) {

    private val loggerClassName: Type = Type.getObjectType(loggerDefinition.loggerInternalName())
    private val ownerClassName = ownerInternalName.replace('/', '.')
    private val packageName = if(ownerClassName.contains('.')) ownerClassName.substring(0, ownerClassName.lastIndexOf('.')) else ""
    private val className = if(ownerClassName.contains('.')) ownerClassName.substring(ownerClassName.lastIndexOf('.') + 1) else ownerClassName
    private val stackAnalyzer = AnalyzerAdapter(ownerInternalName, access, methodName, descriptor, null)
    private val recordedInstructions = mutableListOf<DelayedInstruction>()
    private var lineNumber: Int = 0
    private val labelStacks = mutableMapOf<Label, List<Any>>()

    private fun hasLogger(): Boolean = stackAnalyzer.stack != null && stackAnalyzer.stack.size > 0 && stackAnalyzer.stack.get(0) == this.loggerClassName.internalName

    private fun replay(log: Boolean) {
        this.recordedInstructions.forEach{
                ins -> ins.replay(super.getDelegate(), log)
        }
        this.recordedInstructions.clear()
    }

    override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ ->
                mv.visitFieldInsn(opcode, owner, name, descriptor)
            }
            stackAnalyzer.visitFieldInsn(opcode, owner, name, descriptor)
            if(!hasLogger()) replay(false)
        } else {
            super.visitFieldInsn(opcode, owner, name, descriptor)
            stackAnalyzer.visitFieldInsn(opcode, owner, name, descriptor)
        }
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String?,
        name: String?,
        descriptor: String?,
        isInterface: Boolean
    ) {
        val hadLogger = hasLogger()
        if (hadLogger) {
            recordedInstructions.add { mv, _ ->
                mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            }
        } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
        stackAnalyzer.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        if(hadLogger && !hasLogger()) {
            if(loggerDefinition.isLogMethod(name!!, descriptor!!)) {
                val desc = loggerDefinition.correspondingLogSelectorMethodDescriptor(name, descriptor)
                val selectorName = loggerDefinition.correspondingLogSelectorMethodName(name, descriptor)
                val jumpOut = Label()
                val exitPoint = Label()
                super.visitInsn(Opcodes.DUP) // real stack should have 2 LOGGERS
                val innerOpcode = if (loggerDefinition.isInterface()) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
                super.visitMethodInsn(innerOpcode, loggerDefinition.loggerInternalName(), selectorName, desc, loggerDefinition.isInterface())
                super.visitJumpInsn(Opcodes.IFEQ, jumpOut)
                replay(true)
                super.visitJumpInsn(Opcodes.GOTO, exitPoint)
                super.visitLabel(jumpOut)
                super.visitInsn(Opcodes.POP)
                super.visitLabel(exitPoint)
            } else {
                replay(false)
            }
        }
    }

    override fun visitInsn(opcode: Int) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitInsn(opcode) }
            stackAnalyzer.visitInsn(opcode)
            if(!hasLogger()) replay(false)
        } else {
            super.visitInsn(opcode)
            stackAnalyzer.visitInsn(opcode)
        }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitVarInsn(opcode, varIndex) }
            stackAnalyzer.visitVarInsn(opcode, varIndex)
            if(!hasLogger()) replay(false)
        } else {
            super.visitVarInsn(opcode, varIndex)
            stackAnalyzer.visitVarInsn(opcode, varIndex)
        }
    }


    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ) {
        if (hasLogger()) {
            recordedInstructions.add { mv, b ->
                val modifyAll = modifyAll(b, bootstrapMethodArguments.toList()).toTypedArray()
                mv.visitInvokeDynamicInsn(
                    name,
                    descriptor,
                    bootstrapMethodHandle,
                    *modifyAll
                )
            }
            stackAnalyzer.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)
            if(!hasLogger()) replay(false)
        } else {
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments)
            stackAnalyzer.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)
        }
    }

    private fun modifyAll(mod: Boolean, args: List<Any?>): List<Any?> {
        val list = args.map { a ->
            if (a is String && mod) {
                modifyString(a)
            } else a
        }
        return list
//        val arrayOfAnys: Array<Any> = list.toTypedArray()
//        return arrayOfAnys
    }


    private fun modifyString(original: String): String = stringAdaptor.apply(original, packageName, className, methodName, lineNumber)

    override fun visitLdcInsn(value: Any?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, mod ->
                if (value is String && mod) {
                    mv.visitLdcInsn(modifyString(value))
                } else mv.visitLdcInsn(value)
            }
            stackAnalyzer.visitLdcInsn(value)
            if(!hasLogger()) replay(false)
        } else {
            super.visitLdcInsn(value)
            stackAnalyzer.visitLdcInsn(value)
        }
    }

    override fun visitLineNumber(line: Int, start: Label?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitLineNumber(line, start); lineNumber = line }
            stackAnalyzer.visitLineNumber(line, start)
            if(!hasLogger()) replay(false)
        } else {
            super.visitLineNumber(line, start)
            lineNumber = line
            stackAnalyzer.visitLineNumber(line, start)
        }
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitIincInsn(varIndex, increment) }
            stackAnalyzer.visitIincInsn(varIndex, increment)
            if(!hasLogger()) replay(false)
        } else {
            super.visitIincInsn(varIndex, increment)
            stackAnalyzer.visitIincInsn(varIndex, increment)
        }
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitIntInsn(opcode, operand) }
            stackAnalyzer.visitIntInsn(opcode, operand)
            if(!hasLogger()) replay(false)
        } else {
            super.visitIntInsn(opcode, operand)
            stackAnalyzer.visitIntInsn(opcode, operand)
        }
    }

    override fun visitJumpInsn(opcode: Int, label: Label?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitJumpInsn(opcode, label) }
            stackAnalyzer.visitJumpInsn(opcode, label)
            if(!hasLogger()) replay(false)
            val currentStack = stackAnalyzer.stack ?: mutableListOf<Any>()
            this.labelStacks[label!!] = currentStack
        } else {
            super.visitJumpInsn(opcode, label)
            stackAnalyzer.visitJumpInsn(opcode, label)
            val currentStack = stackAnalyzer.stack ?: mutableListOf<Any>()
            this.labelStacks[label!!] = currentStack
        }

    }

    override fun visitLabel(label: Label?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitLabel(label) }
            stackAnalyzer.visitLabel(label)
            if(!hasLogger()) replay(false)
            val st = labelStacks.getOrDefault(label!!, mutableListOf())
            stackAnalyzer.stack = st
        } else {
            super.visitLabel(label)
            stackAnalyzer.visitLabel(label)
            val st = labelStacks.getOrDefault(label!!, mutableListOf())
            stackAnalyzer.stack = st
        }

    }

    override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitLookupSwitchInsn(dflt, keys, labels) }
            stackAnalyzer.visitLookupSwitchInsn(dflt, keys, labels)
            if(!hasLogger()) replay(false)
        } else {
            super.visitLookupSwitchInsn(dflt, keys, labels)
            stackAnalyzer.visitLookupSwitchInsn(dflt, keys, labels)
        }
    }

    override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitMultiANewArrayInsn(descriptor, numDimensions) }
            stackAnalyzer.visitMultiANewArrayInsn(descriptor, numDimensions)
            if(!hasLogger()) replay(false)
        } else {
            super.visitMultiANewArrayInsn(descriptor, numDimensions)
            stackAnalyzer.visitMultiANewArrayInsn(descriptor, numDimensions)
        }
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitTableSwitchInsn(min, max, dflt, *labels) }
            stackAnalyzer.visitTableSwitchInsn(min, max, dflt, *labels)
            if(!hasLogger()) replay(false)
        } else {
            super.visitTableSwitchInsn(min, max, dflt, *labels)
            stackAnalyzer.visitTableSwitchInsn(min, max, dflt, *labels)
        }
    }

    override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitTryCatchBlock(start, end, handler, type) }
            stackAnalyzer.visitTryCatchBlock(start, end, handler, type)
            if(!hasLogger()) replay(false)
        } else {
            super.visitTryCatchBlock(start, end, handler, type)
            stackAnalyzer.visitTryCatchBlock(start, end, handler, type)
        }
    }

    override fun visitTypeInsn(opcode: Int, type: String?) {
        if (hasLogger()) {
            recordedInstructions.add { mv, _ -> mv.visitTypeInsn(opcode, type) }
            stackAnalyzer.visitTypeInsn(opcode, type)
            if(!hasLogger()) replay(false)
        } else {
            super.visitTypeInsn(opcode, type)
            stackAnalyzer.visitTypeInsn(opcode, type)
        }
    }

    override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) {
        super.visitFrame(type, numLocal, local, numStack, stack)
        stackAnalyzer.visitFrame(type, numLocal, local, numStack, stack)
    }

    override fun visitLocalVariable(
        name: String?,
        descriptor: String?,
        signature: String?,
        start: Label?,
        end: Label?,
        index: Int
    ) {
        super.visitLocalVariable(name, descriptor, signature, start, end, index)
        stackAnalyzer.visitLocalVariable(name, descriptor, signature, start, end, index)
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitMaxs(maxStack, maxLocals)
        stackAnalyzer.visitMaxs(maxStack, maxLocals)
    }

    override fun visitEnd() {
        if(this.recordedInstructions.size > 0) {
            throw IllegalStateException("error on method ${this.methodName}: delayed instructions is not empty: ${this.recordedInstructions}")
        }
        super.visitEnd()
    }

}
