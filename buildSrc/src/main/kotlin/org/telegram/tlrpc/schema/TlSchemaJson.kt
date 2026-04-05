package org.telegram.tlrpc.schema

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TlSchemaJson(
    @Json(name = "constructors")
    val constructors: List<JsonTlConstructor>,

    @Json(name = "methods")
    val methods: List<JsonTlMethod>,
) {
    sealed class JsonTlObject {
        abstract val magic: String
        abstract val params: List<JsonTlConstructorParam>
        abstract val name: String
        abstract val type: String
        abstract val layer: Int?
    }

    @JsonClass(generateAdapter = true)
    data class JsonTlMethod(
        @Json(name = "id")
        override val magic: String,

        @Json(name = "method")
        override val name: String,

        @Json(name = "params")
        override val params: List<JsonTlConstructorParam>,

        @Json(name = "type")
        override val type: String,

        @Json(name = "layer")
        override val layer: Int? = null
    ): JsonTlObject()

    @JsonClass(generateAdapter = true)
    data class JsonTlConstructor(
        @Json(name = "id")
        override val magic: String,

        @Json(name = "predicate")
        override val name: String,

        @Json(name = "params")
        override val params: List<JsonTlConstructorParam>,

        @Json(name = "type")
        override val type: String,

        @Json(name = "layer")
        override val layer: Int? = null
    ): JsonTlObject()

    @JsonClass(generateAdapter = true)
    data class JsonTlConstructorParam(
        @Json(name = "name")
        val name: String,

        @Json(name = "type")
        val type: String
    )
}
