package com.atlassian.prosemirror.model.parser

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

@Suppress("UNCHECKED_CAST")
val JSON: Json by lazy {
  Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
      contextual(AnySerializer)
      contextual(String.serializer())
      contextual(Int.serializer())
      contextual(Double.serializer())
      contextual(Long.serializer())
      contextual(Boolean.serializer())
      contextual(ListSerializer(AnySerializer) as KSerializer<ArrayList<*>>)
      contextual(MapSerializer(String.serializer(), AnySerializer) as KSerializer<LinkedHashMap<*, *>>)
    }
  }
}

// TODO Aleksei: reuse one from adf-utils
internal object AnySerializer : KSerializer<Any> {
  @OptIn(ExperimentalSerializationApi::class)
  override val descriptor: SerialDescriptor = AnySerialDescriptor("kotlin.Any", PrimitiveKind.STRING)

  @OptIn(ExperimentalSerializationApi::class)
  override fun serialize(encoder: Encoder, value: Any) {
    when (value) {
      is Boolean -> encoder.encodeBoolean(value)
      is Byte -> encoder.encodeByte(value)
      is Char -> encoder.encodeChar(value)
      is Double -> encoder.encodeDouble(value)
      is Float -> encoder.encodeFloat(value)
      is Long -> encoder.encodeLong(value)
      is Short -> encoder.encodeShort(value)
      is Int -> encoder.encodeInt(value)
      else -> {
        val serializer = encoder.serializersModule.getContextual(value::class)
        if (serializer != null) {
          @Suppress("UNCHECKED_CAST")
          encoder.encodeSerializableValue(serializer as SerializationStrategy<Any>, value)
        } else {
          encoder.encodeString(value.toString())
        }
      }
    }
  }
  override fun deserialize(decoder: Decoder): Any {
    val res: JsonElement = (decoder as JsonDecoder).decodeJsonElement()
    return fromJsonElement(decoder, res)
  }

  private fun fromJsonElement(decoder: Decoder, res: JsonElement): Any {
    return when (res) {
      is JsonPrimitive -> {
        if (res.isString) {
          res.content
        } else {
          when (res.content) {
            "true" -> true
            "false" -> false
            else -> {
              res.content.toIntOrNull()
                ?: res.content.toLongOrNull()
                ?: res.content.toDoubleOrNull()
                ?: res.content
            }
          }
        }
      }
      is JsonArray -> {
        res.map {
          fromJsonElement(decoder, it)
        }
      }
      is JsonObject -> {
        res.entries.associate {
          it.key to fromJsonElement(decoder, it.value)
        }
      }
      else -> {
        res.toString()
      }
    }
  }

  @ExperimentalSerializationApi
  internal class AnySerialDescriptor(
    override val serialName: String,
    override val kind: PrimitiveKind
  ) : SerialDescriptor {
    override val elementsCount: Int get() = 0
    override fun getElementName(index: Int): String = error()
    override fun getElementIndex(name: String): Int = error()
    override fun isElementOptional(index: Int): Boolean = error()
    override fun getElementDescriptor(index: Int): SerialDescriptor = error()
    override fun getElementAnnotations(index: Int): List<Annotation> = error()
    override fun toString(): String = "AnyDescriptor"
    private fun error(): Nothing = throw IllegalStateException("Any descriptor does not have elements")
  }
}
