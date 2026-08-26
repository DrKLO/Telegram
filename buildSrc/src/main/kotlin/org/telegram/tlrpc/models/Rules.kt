package org.telegram.tlrpc.models

data class Rules(
    val databaseTypes: Set<String>,
    val ignoredTypes: Set<String>,
    val ignoredConstructors: Map<String, Set<String>>,
) {
    fun filterConstructor(key: TlTypeName): Boolean {
        if (key.type in ignoredTypes) {
            return false
        }

        val ignored = ignoredConstructors[key.type] ?: emptySet()
        if (key.predicate in ignored) {
            return false
        }

        return true
    }
}