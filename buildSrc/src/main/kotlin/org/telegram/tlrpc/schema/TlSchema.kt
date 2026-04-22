package org.telegram.tlrpc.schema

data class TlSchema(
    val constructors: List<TlSchemaObject>,
    val methods: List<TlSchemaObject>
) {
    val typesAll = constructors.groupBy { it.type }
    val typesEnum = typesAll.filterValues { it.count { c -> c.params.isNotEmpty() } == 0 }.filterValues { it.size > 1 }
    val typesSingleton = typesAll.filterValues { it.count { c -> c.params.isNotEmpty() } == 0 }.filterValues { it.size == 1 }.keys

    val dependenciesDirect = typesAll
        .mapValues { getParamTypeNamesFromConstructors(it.value) }
        .filterValues { it.isNotEmpty() }

    val magics = constructors.map { it.magic } + methods.map { it.magic }

    fun applyAllowedTypes(allowedTypes: Set<String>): TlSchema {
        return TlSchema(
            constructors = this.constructors.filter { it.type in allowedTypes },
            methods = this.methods.filter { it.type in allowedTypes },
        )
    }

    fun applyTypesFilter(ignoredTypes: Set<String>): TlSchema {
        return TlSchema(
            constructors = this.constructors.filter { it.type !in ignoredTypes },
            methods = this.methods.filter { it.type !in ignoredTypes },
        )
    }

    fun applyConstructorsFilter(ignoredConstructors: Map<String, Set<String>>): TlSchema {
        return TlSchema(
            constructors = this.constructors.filter {
                val type = it.type
                val ignoredConstructors2 = ignoredConstructors[type] ?: return@filter true
                return@filter it.name !in ignoredConstructors2
            },
            methods = this.methods,
        )
    }



    companion object {
        fun from(json: TlSchemaJson): TlSchema {
            return TlSchema(
                constructors = json.constructors.map { TlSchemaObject.from(it) }.filter { it.type != "Vector t" },
                methods = json.methods.map { TlSchemaObject.from(it) },
            )
        }

        private fun getParamTypeNamesFromConstructors(constructors: List<TlSchemaObject>): Set<String> {
            return getParamTypeNames(constructors.map { it.params.map { p -> p.type } }.flatten())
        }

        private fun getParamTypeNames(types: List<TlSchemaParamType>): Set<String> {
            return types.mapNotNull{ getParamTypeName(it) }.toSet()
        }

        private fun getParamTypeName(paramType: TlSchemaParamType): String? {
            return when (paramType) {
                is TlSchemaParamType.Primary.X -> null
                is TlSchemaParamType.Flags -> null
                is TlSchemaParamType.Flag.True -> null
                is TlSchemaParamType.Primary.Primitive -> null
                is TlSchemaParamType.Flag.Optional -> getParamTypeName(paramType.type)
                is TlSchemaParamType.Primary.Vector -> getParamTypeName(paramType.type)
                is TlSchemaParamType.Primary.Object -> paramType.name
            }
        }
    }
}