package org.telegram.tgnet.test

import com.appmattus.kotlinfixture.Fixture
import com.appmattus.kotlinfixture.config.Configuration
import com.appmattus.kotlinfixture.config.ConfigurationBuilder
import com.appmattus.kotlinfixture.decorator.nullability.AlwaysNullStrategy
import com.appmattus.kotlinfixture.decorator.nullability.NeverNullStrategy
import com.appmattus.kotlinfixture.decorator.nullability.NullabilityStrategy
import com.appmattus.kotlinfixture.decorator.nullability.RandomlyNullStrategy
import com.appmattus.kotlinfixture.decorator.nullability.nullabilityStrategy
import com.appmattus.kotlinfixture.decorator.recursion.RecursionStrategy
import com.appmattus.kotlinfixture.decorator.recursion.recursionStrategy
import org.junit.BeforeClass
import org.telegram.tgnet.InputSerializedData
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.model.TlGen_Object
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals

open class BaseSchemeTest {
    companion object {
        lateinit var fixture: Fixture
        protected lateinit var safeRecursionStrategy: SafeRecursionStrategy

        protected lateinit var buffer: NativeByteBuffer
        protected lateinit var buffer2: NativeByteBuffer

        @JvmStatic
        @BeforeClass
        fun setup() {
            fixture = Fixture()
            safeRecursionStrategy = SafeRecursionStrategy(fixture)

            buffer = NativeByteBuffer(1024 * 1024)
            buffer2 = NativeByteBuffer(1024 * 1024)
        }
    }

    protected fun test_TLdeserialize(
        clazz: KClass<out TlGen_Object>,
        deserializer: ((stream: InputSerializedData, constructor: Int, exception: Boolean) -> TLObject),
        isLegacyLayer: Int? = null
    ) {
        createConfigs(clazz).forEach {
            @Suppress("DEPRECATION_ERROR")
            val generated = fixture.create(clazz, it) as TlGen_Object

            try {
                buffer.rewind()
                generated.serializeToStream(buffer)
                val expectedPosition = buffer.position()

                buffer.rewind()
                val result = deserializer.invoke(buffer, buffer.readInt32(true), true)
                assertEquals(expectedPosition, buffer.position())

                buffer2.rewind()
                result.serializeToStream(buffer2)

                if (isLegacyLayer != null/* && expectedPosition != buffer2.position() */) {
                    buffer2.rewind()
                    val result2 = deserializer.invoke(buffer2, buffer2.readInt32(true), true)
                } else {
                    assertEquals(expectedPosition, buffer2.position())
                    assertBuffersEquals(buffer, buffer2)
                }
            } catch (t: Throwable) {
                println(generated.toString())
                throw t
            }
        }
    }

    class SafeRecursionStrategy(private val fixture: Fixture) : RecursionStrategy {
        override fun handleRecursion(type: KType, stack: Collection<KType>): Any? {
            if (type.isMarkedNullable) {
                return null
            }

            val strategies = fixture.fixtureConfiguration.strategies.toMutableMap()
            strategies[RecursionStrategy::class] = this
            strategies[NullabilityStrategy::class] = AlwaysNullStrategy

            @Suppress("DEPRECATION_ERROR")
            return fixture.create(type, fixture.fixtureConfiguration.copy(
                strategies = strategies,
                repeatCount = { 0 },
            ))
        }
    }



    private fun assertBuffersEquals(buffer1: NativeByteBuffer, buffer2: NativeByteBuffer) {
        assertEquals(buffer1.position(), buffer2.position(), "Buffer positions not equals")
        val bytes = buffer1.position()

        buffer1.rewind()
        buffer2.rewind()


        for (i in 0..<bytes) {
            assertEquals(buffer1.readByte(true), buffer2.readByte(true), "index: $i")
        }
    }

    private fun createConfigs(clazz: KClass<out TlGen_Object>): List<Configuration> {
        val nullableFields = clazz.memberProperties.filter { it.returnType.isMarkedNullable }
        val neverNull = ConfigurationBuilder().apply {
            recursionStrategy(safeRecursionStrategy)
            nullabilityStrategy(NeverNullStrategy)
        }.build()

        if (nullableFields.isEmpty()) {
            return listOf(neverNull)
        }

        val randomNull = ConfigurationBuilder().apply {
            recursionStrategy(safeRecursionStrategy)
            nullabilityStrategy(RandomlyNullStrategy)
        }.build()

        val alwaysNull = ConfigurationBuilder().apply {
            recursionStrategy(safeRecursionStrategy)
            nullabilityStrategy(AlwaysNullStrategy)
        }.build()

        val result = mutableListOf(neverNull, alwaysNull)

        @Suppress("DEPRECATION_ERROR")
        nullableFields.forEach { field ->
            result.add(alwaysNull.copy(
                properties = mapOf(clazz to mapOf(field.name to { fixture.create(field.returnType, neverNull) }))
            ))
            result.add(randomNull.copy(
                properties = mapOf(clazz to mapOf(field.name to { fixture.create(field.returnType, neverNull) }))
            ))
        }
        
        repeat(maxOf(25 - nullableFields.size, 1)) {
            result.add(randomNull)
        }

        return result
    }
}