package org.Tajgram.tlrpc

import org.Tajgram.tlrpc.models.*
import org.Tajgram.tlrpc.schema.TlSchemaJson
import org.Tajgram.tlrpc.schema.TlSchemaJsonParser
import org.Tajgram.tlrpc.schema.TlSchemaObject

object SchemeJsonParser {
    fun parse(version: TlSchemeVersion, json: String): TlScheme {
        val scheme = TlSchemaJsonParser.parse(json)

        val constructors = scheme.constructors.map(::parseConstructor)
        val methods = scheme.methods.map(::parseConstructor)

        return TlScheme(
            version = version,
            json = scheme,
            constructors2 = constructors,
            methods2 =  methods
        )
    }

    private fun parseConstructor(constructor: TlSchemaJson.JsonTlObject): TlObject {
        val result = TlSchemaObject.from(constructor)

        return TlObject(
            key = TlTypeKey(
                name = TlTypeName(type = result.type, predicate = result.name),
                constructorId = result.magic
            ),
            params = TlParams(result.params.map {
                TlParam(it.name, it.type)
            })
        )
    }
}
