package org.telegram.tlrpc

import org.telegram.tlrpc.models.*
import org.telegram.tlrpc.schema.TlSchemaParamType

object SchemeTlValidator {
    fun validate(scheme: TlScheme): TlSchemeWithMeta {
        val grouped = scheme.constructors2.groupBy { it.key.name.type }

        val types = grouped.keys

        val isValid = scheme.constructors2.all { c ->
            c.params.list.all { p -> validateParamType(types, p.type) }
        } && scheme.methods2.all { c ->
            true // validateParamType(types, c.type) && c.params.all { p -> validateParamType(types, p.type) }
        }

        val dependenciesDirect = grouped.mapValues {
            it.value.map { c -> getDirectDependency(c) }.flatten().toSet()
        }

        val dependenciesTransitive = collectAllTransitiveDependencies(dependenciesDirect)

        return TlSchemeWithMeta(
            scheme = scheme,
            types = grouped,
            isValid = isValid,
            dependenciesDirect = dependenciesDirect,
            dependenciesTransitive = dependenciesTransitive,

            constructorsByName = scheme.constructors2.associateBy { it.key.name },
            methodsByName = scheme.methods2.associateBy { it.key.name.predicate }
        )
    }

    fun findShortestPaths(
        graph: Map<String, Set<String>>,
        start: String
    ): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        val queue: ArrayDeque<List<String>> = ArrayDeque()
        val visited = mutableSetOf<String>()

        queue.add(listOf(start))
        visited.add(start)
        result[start] = listOf(start)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()

            for (neighbor in graph[current].orEmpty()) {
                if (neighbor !in visited) {
                    val newPath = path + neighbor
                    result[neighbor] = newPath
                    visited.add(neighbor)
                    queue.add(newPath)
                }
            }
        }

        return result
    }

    private fun getDirectDependency(constructor: TlObject): Set<String> {
        return constructor.params.list.mapNotNull { getParamTypeObjectName(it.type) }.toSet()
    }

    private fun collectAllTransitiveDependencies(graph: Map<String, Set<String>>): Map<String, Set<String>> {
        val cache = mutableMapOf<String, Set<String>>()

        fun dfs(node: String, visited: MutableSet<String>): Set<String> {
            if (node in cache) return cache[node]!!

            val result = mutableSetOf<String>()
            for (dep in graph[node].orEmpty()) {
                if (visited.add(dep)) {
                    result += dep
                    result += dfs(dep, visited)
                }
            }
            cache[node] = result
            return result
        }

        return graph.keys.associateWith { dfs(it, mutableSetOf()) }
    }

    private fun validateParamType(types: Set<String>, paramType: TlSchemaParamType): Boolean {
        return getParamTypeObjectName(paramType)?.let { it in types } ?: true
    }

    private fun getParamTypeObjectName(paramType: TlSchemaParamType): String? {
        return when (paramType) {
            is TlSchemaParamType.Primary.X -> null
            is TlSchemaParamType.Flags -> null
            is TlSchemaParamType.Flag.True -> null
            is TlSchemaParamType.Primary.Primitive -> null
            is TlSchemaParamType.Flag.Optional -> getParamTypeObjectName(paramType.type)
            is TlSchemaParamType.Primary.Vector -> getParamTypeObjectName(paramType.type)
            is TlSchemaParamType.Primary.Object -> paramType.name
        }
    }
}
