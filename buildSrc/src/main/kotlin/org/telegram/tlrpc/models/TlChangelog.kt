package org.telegram.tlrpc.models

import org.telegram.tlrpc.schema.TlSchemaParamType

data class TlSchemeChangelog(
    val constructors: List<TlConstructorChangelog>,
    val methods: List<TlMethodChangelog>
)

sealed class TlParamChangelog {
    abstract val name: String

    data class Added(
        override val name: String,
        val newType: TlSchemaParamType,
    ): TlParamChangelog()

    data class Removed(
        override val name: String,
        val oldType: TlSchemaParamType,
    ): TlParamChangelog()

    data class Changed(
        override val name: String,
        val oldType: TlSchemaParamType,
        val newType: TlSchemaParamType,
    ): TlParamChangelog()
}

sealed class TlMethodChangelog {
    abstract val method: String

    data class Added(
        override val method: String,
    ): TlMethodChangelog()

    data class Removed(
        override val method: String,
    ): TlMethodChangelog()

    data class Changed(
        override val method: String,
        val changelog: List<TlParamChangelog>,
    ): TlMethodChangelog()
}

sealed class TlConstructorChangelog {
    abstract val constructor: TlTypeName

    data class Added(
        override val constructor: TlTypeName
    ): TlConstructorChangelog()

    data class Removed(
        override val constructor: TlTypeName
    ): TlConstructorChangelog()

    data class Changed(
        override val constructor: TlTypeName,
        val changelog: List<TlParamChangelog>
    ): TlConstructorChangelog()
}
