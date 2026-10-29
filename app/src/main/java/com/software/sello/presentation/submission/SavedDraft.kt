package com.software.sello.presentation.submission

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * A form's draft kept in [saved] under one [key], so it outlives process death
 * (ARCHITECTURE §6). The draft is a `@Serializable` class of strings, numbers, booleans,
 * enums and lists of them; it is stored as a list of strings, which the handle keeps on
 * any platform. A stored draft this class cannot read back is treated as none.
 */
class SavedDraft<T>(
    private val saved: SavedStateHandle,
    private val key: String,
    private val serializer: KSerializer<T>
) {
    fun read(): T? = saved.get<ArrayList<String?>>(key)?.let {
        try {
            serializer.deserialize(ListDecoder(it))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun write(value: T) {
        val out = ArrayList<String?>()
        serializer.serialize(ListEncoder(out), value)
        saved[key] = out
    }

    fun clear() {
        saved.remove<Any>(key)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class ListEncoder(private val out: ArrayList<String?>) : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeValue(value: Any) {
        out += value.toString()
    }

    override fun encodeNull() {
        out += null
    }

    override fun encodeNotNullMark() {
        out += PRESENT
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        out += enumDescriptor.getElementName(index)
    }

    override fun beginCollection(
        descriptor: SerialDescriptor,
        collectionSize: Int
    ): CompositeEncoder {
        out += collectionSize.toString()
        return this
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class ListDecoder(private val input: List<String?>) : AbstractDecoder() {
    private var position = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private fun next(): String? = input.getOrElse(position++) {
        throw SerializationException("The saved draft ends early")
    }

    private fun value(): String = next() ?: throw SerializationException("A saved value is missing")

    override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        throw SerializationException("The saved draft is read in order")

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = value().toInt()

    override fun decodeNotNullMark(): Boolean = next() != null

    override fun decodeNull(): Nothing? = null

    override fun decodeValue(): Any = throw SerializationException("Not a saved draft value")

    override fun decodeString(): String = value()

    override fun decodeBoolean(): Boolean = value().toBooleanStrict()

    override fun decodeInt(): Int = value().toInt()

    override fun decodeLong(): Long = value().toLong()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(value()).takeIf { it != CompositeDecoder.UNKNOWN_NAME }
            ?: throw SerializationException("Not a saved draft value")
}

private const val PRESENT = "+"
