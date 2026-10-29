package com.software.sello.presentation.submission

import com.software.sello.domain.policy.AmountDraft
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** What was typed, as text: null for nothing, otherwise read back with [AmountDraft.paste]. */
fun AmountDraft.typed(): String? = when (this) {
    AmountDraft.Empty -> null
    is AmountDraft.Digits -> digits
    is AmountDraft.Rejected -> original
}

/** An amount draft in a [SavedDraft]: what was typed, pasted back on restoration. */
object AmountDraftSerializer : KSerializer<AmountDraft> {
    private val text = String.serializer().nullable

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor("sello.AmountDraft", text.descriptor)

    override fun serialize(encoder: Encoder, value: AmountDraft) =
        encoder.encodeSerializableValue(text, value.typed())

    override fun deserialize(decoder: Decoder): AmountDraft =
        decoder.decodeSerializableValue(text)?.let(AmountDraft::paste) ?: AmountDraft.Empty
}

/** A calendar date in a [SavedDraft], as ISO text. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("sello.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = try {
        LocalDate.parse(decoder.decodeString())
    } catch (failure: DateTimeParseException) {
        throw SerializationException("Not a saved date", failure)
    }
}
