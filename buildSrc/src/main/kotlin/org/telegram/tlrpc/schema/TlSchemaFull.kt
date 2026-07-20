package org.telegram.tlrpc.schema

import org.telegram.tlrpc.SchemeTlValidator
import org.telegram.tlrpc.models.Rules

data class TlSchemaFull(
    val schema: TlSchema,
    val encrypted: TlSchema,
    val history: Map<Int, TlSchema>,
    val layer: Int
) {
    val magicsAll = (schema.magics + encrypted.magics + history.values.map { it.magics }.flatten()).toSet()

    fun applyRules(rules: Rules): TlSchemaFull {
        val history = this.history.mapValues { it.value
         .applyTypesFilter(rules.ignoredTypes)
         .applyConstructorsFilter(rules.ignoredConstructors)
        }

        val legacyPaths = history.map { it ->
            val layer = it.key
            val schema = it.value
            val paths = rules.databaseTypes.map { type -> SchemeTlValidator.findShortestPaths(schema.dependenciesDirect, type) }
            val pathsLists = paths
                .map { it.entries }.flatten().toSet()
                .map { it.key to it.value }.groupBy { it.first }
                .mapValues { it.value.map { x -> x.second }.toSet().toList() }

            layer to (schema.applyAllowedTypes(pathsLists.keys) to pathsLists)
        }.toMap()

        return TlSchemaFull(
            schema = schema
                .applyTypesFilter(rules.ignoredTypes)
                .applyConstructorsFilter(rules.ignoredConstructors),
            history = legacyPaths.mapValues { it.value.first },
            encrypted = encrypted,
            layer = layer
        )
    }


    fun getConstructorHistory(type: String, name: String): Map<Int, TlSchemaObject?> {
        val x = history.mapValues {
            it.value.constructors.find { c -> c.name == name && c.type == type }
        }.toMutableMap()

        x.put(layer, schema.constructors.find { c -> c.name == name && c.type == type })

        return x.toMap()
    }


    fun getAllConstructorsHistory(): Map<Pair<String, String>, Map<Int, TlSchemaObject?>> {
        val keys = (schema.constructors.map { it.type to it.name } +
            history.values.map { h -> h.constructors.map { it.type to it.name } }.flatten()).toSet()

        return keys.associate { key -> key to getConstructorHistory(key.first, key.second) }
    }
}