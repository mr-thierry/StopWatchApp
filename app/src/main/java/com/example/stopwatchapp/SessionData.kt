package com.example.stopwatchapp

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Lap(
    val lapNumber: Int,
    val durationMs: Long,
    val distanceM: Int,
    val paceMinKm: String
)

@Serializable
data class SessionState(
    val isRunning: Boolean = false,
    val startTime: Long = 0L,
    val elapsedTime: Long = 0L,
    val laps: SerializableImmutableList<Lap> = persistentListOf(),
    val trackDistanceM: Int = 370, // Default to Delson
    val isUiVisible: Boolean = true
)


typealias SerializableImmutableList<T> = @Serializable(ImmutableListSerializer::class) ImmutableList<T>

@Serializer(forClass = ImmutableList::class)
class ImmutableListSerializer<T>(private val dataSerializer: KSerializer<T>) : KSerializer<ImmutableList<T>> {
    @OptIn(SealedSerializationApi::class)
    private class PersistentListDescriptor : SerialDescriptor by serialDescriptor<List<String>>() {
        override val serialName: String = "kotlinx.serialization.immutable.ImmutableList"
    }

    override val descriptor: SerialDescriptor = PersistentListDescriptor()
    override fun serialize(encoder: Encoder, value: ImmutableList<T>) {
        return ListSerializer(dataSerializer).serialize(encoder, value.toList())
    }

    override fun deserialize(decoder: Decoder): ImmutableList<T> {
        return ListSerializer(dataSerializer).deserialize(decoder).toPersistentList()
    }
}