package org.telegram.tlrpc

import org.telegram.tlrpc.models.*
import org.telegram.tlrpc.schema.TlSchemaJson
import org.telegram.tlrpc.schema.TlSchemaJsonParser
import org.telegram.tlrpc.schema.TlSchemaObject
import org.telegram.tlrpc.schema.TlSchemaParamType

object SchemeJsonParser {
    fun parse(version: TlSchemeVersion, json: String): TlScheme {
        val scheme = TlSchemaJsonParser.parse(json)

        val predicateTypes = scheme.constructors.associate { it.name to it.type }
        val constructors = scheme.constructors.map { parseConstructor(predicateTypes, it) }
        val methods = scheme.methods.map { parseConstructor(predicateTypes, it) }

        return TlScheme(
            version = version,
            json = scheme,
            constructors2 = constructors,
            methods2 =  methods
        )
    }

    private fun parseConstructor(predicateTypes: Map<String, String>, constructor: TlSchemaJson.JsonTlObject): TlObject {
        val result = TlSchemaObject.from(constructor)

        return TlObject(
            key = TlTypeKey(
                name = TlTypeName(type = result.type, predicate = result.name),
                constructorId = result.magic
            ),
            params = TlParams(result.params.map {
                TlParam(it.name, resolveBareType(predicateTypes, it.type))
            })
        )
    }

    // A param typed by a constructor predicate (`agent.settings`) is a bare type:
    // keep the boxed type as the name, so dependencies and validation see a real type.
    private fun resolveBareType(predicateTypes: Map<String, String>, type: TlSchemaParamType): TlSchemaParamType {
        return when (type) {
            is TlSchemaParamType.Primary.Object -> predicateTypes[type.name]
                ?.let { TlSchemaParamType.Primary.Object(name = it, bareConstructor = type.name) }
                ?: type
            is TlSchemaParamType.Primary.Vector -> type.copy(type = resolveBareType(predicateTypes, type.type) as TlSchemaParamType.Primary)
            is TlSchemaParamType.Flag.Optional -> type.copy(type = resolveBareType(predicateTypes, type.type) as TlSchemaParamType.Primary)
            else -> type
        }
    }
}
