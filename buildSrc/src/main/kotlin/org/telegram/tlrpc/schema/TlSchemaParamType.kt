package org.telegram.tlrpc.schema

sealed class TlSchemaParamType {

    sealed class Primary: TlSchemaParamType() {
        data class Primitive(val type: TlSchemaPrimitiveType): Primary()

        data class Vector(
            val type: Primary
        ): Primary()

        data class Object(
            val name: String
        ) : Primary()

        data object X : Primary()
    }

    data object Flags : TlSchemaParamType()

    sealed class Flag: TlSchemaParamType() {
        abstract val flag: String
        abstract val num: Int

        data class True(
            override val flag: String,
            override val num: Int
        ): Flag()

        data class Optional(
            val type: Primary,
            override val flag: String,
            override val num: Int
        ): Flag()
    }

    companion object {
        fun parse(flags: Set<String>, typeName: String): TlSchemaParamType {
            if (typeName == "#") {
                return Flags
            }

            if (typeName == "X" || typeName == "!X") {
                return Primary.X
            }

            if (typeName == "int" || typeName == "int32") {
                return Primary.Primitive(TlSchemaPrimitiveType.INT)
            }

            if (typeName == "long") {
                return Primary.Primitive(TlSchemaPrimitiveType.LONG)
            }

            if (typeName == "int256") {
                return Primary.Primitive(TlSchemaPrimitiveType.INT256)
            }

            if (typeName == "bytes") {
                return Primary.Primitive(TlSchemaPrimitiveType.BYTES)
            }

            if (typeName == "string") {
                return Primary.Primitive(TlSchemaPrimitiveType.STRING)
            }

            if (typeName == "double") {
                return Primary.Primitive(TlSchemaPrimitiveType.DOUBLE)
            }

            for (flag in flags) {
                if (typeName.startsWith(flag)) {
                    if (typeName[flag.length] == '.') {
                        val flagTypeName = typeName.substring(
                            startIndex = typeName.indexOf('?', startIndex = flag.length) + 1
                        )
                        val flagNumber = Integer.parseInt(typeName.substring(
                            startIndex = flag.length + 1,
                            endIndex = typeName.indexOf('?', startIndex = flag.length)
                        ))

                        return if (flagTypeName == "true") {
                            Flag.True(
                                flag = flag,
                                num = flagNumber
                            )
                        } else {
                            Flag.Optional(
                                parse(flags, flagTypeName) as Primary,
                                flag = flag,
                                num = flagNumber
                            )
                        }
                    }
                }
            }

            if (typeName.startsWith("Vector") or typeName.startsWith("vector")) {
                return Primary.Vector(type = parse(flags,
                    typeName.substring(startIndex = 7, endIndex = typeName.length - 1)
                ) as Primary)
            }

            return Primary.Object(typeName)
        }
    }
}