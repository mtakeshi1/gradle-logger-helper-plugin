package gradle.logger.helper.plugin

import org.gradle.internal.impldep.org.junit.Assert
import org.junit.jupiter.api.Test

class ClassModificationTest : MethodVisitorBaseTest(){

    @Test
    fun `test simple modification`() {
        val map = mutableMapOf<String, MutableList<Array<Any>>>()
        val a = modifyClassInjectLogger(SomeClass::class.java, mapOf(), map)

        Assert.assertTrue(map.isEmpty())
    }

    @Test
    fun `test minimal class`() {
        modifyClassCreateInstance(MinimalClass::class.java)
    }

}
