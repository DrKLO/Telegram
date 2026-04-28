package org.telegram.tlrpc

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import org.telegram.tlrpc.models.TlObjectWithLayer
import org.telegram.tlrpc.models.TlParam
import org.telegram.tlrpc.models.TlParamOptionalKey
import org.telegram.tlrpc.schema.TlSchemaParamType
import org.telegram.tlrpc.models.TlParams
import org.telegram.tlrpc.schema.TlSchemaPrimitiveType

object SchemeCodeGen {
    private val CLASS_OUTPUT = ClassName("org.telegram.tgnet", "OutputSerializedData")

    fun generateDataClass(tl: TlObjectWithLayer, superclass: TypeName?): TypeSpec {
        val className = tl.codegenDataClassName
        val params = tl.tl.params

        val builder = if (params.isEmpty) {
            TypeSpec.objectBuilder(className)
        } else {
            TypeSpec.classBuilder(className)
        }

        builder.addModifiers(KModifier.DATA)
        params.multiParamsInOneFlag // params.multiFlags
            .map { generateMultiFlagDataClass(it.key, it.value) }
            .forEach(builder::addType)

        if (!params.isEmpty) {
            builder.addType(TypeSpec.companionObjectBuilder().addProperty(createConstructorIdProp(tl.tl.key.constructorId)).build())
            builder.primaryConstructor(
                FunSpec.constructorBuilder().also {
                    generateDataClassParameters(params).forEach(it::addParameter)
                }.build()
            )
        } else {
            builder.addProperty(createConstructorIdProp(tl.tl.key.constructorId))
        }

        generateDataClassProperties(params).forEach(builder::addProperty)
        generateDataClassFlagGetters(params).forEach(builder::addProperty)
        builder.addFunction(generateSerializeFunction(params))
        superclass?.let { builder.superclass(it) }

        if (superclass == null) {
            builder.addSuperinterface(ClassName("org.telegram.tgnet.model", "TlGen_Object"))
        }

        return builder.build()
    }


    private fun generateDataClassProperties(params: TlParams): List<PropertySpec> {
        val props1 = params.list.filter {
            it.optionalKey !in params.multiParamsInOneFlag.keys &&
            !(it.optionalKey in params.singleParamWithTrueFlag.keys && it.type is TlSchemaParamType.Flag.True)
        }.mapNotNull(::generateDataClassProperty)
        val props2 = params.multiParamsInOneFlag.keys.map(::generateDataClassProperty)

        val props3 = params.singleParamWithTrueFlag.toList().map { it.second }.map { param ->
            PropertySpec.builder(param.find { it.type is TlSchemaParamType.Flag.True }!!.codegenName, com.squareup.kotlinpoet.BOOLEAN)
                .initializer("${ param.find { it.type !is TlSchemaParamType.Flag.True }!!.codegenName } != null")
                .build()
        }
        val props4 = params.list.filter { it.type is TlSchemaParamType.Flag.True && it.optionalKey in params.multiParamsInOneFlag.keys }.map { param ->
            PropertySpec.builder(param.codegenName, com.squareup.kotlinpoet.BOOLEAN)
                .initializer("${ param.optionalKey!!.codegenMultiFlagFieldName } != null")
                .build()
        }



        return props1 + props2 + props3 + props4
    }

    private fun generateDataClassParameters(params: TlParams): List<ParameterSpec> {
        val props1 = params.list.filter {
            it.optionalKey !in params.multiParamsInOneFlag.keys &&
            !(it.optionalKey in params.singleParamWithTrueFlag.keys && it.type is TlSchemaParamType.Flag.True)
        }.mapNotNull(::generateDataClassParameter)
        val props2 = params.multiParamsInOneFlag.keys.map(::generateDataClassParameter)

        return props1 + props2
    }



    private fun generateDataClassProperty(param: TlParam): PropertySpec? {
        val type = mapTlParamTypeToKotlinType(param.type)
        return type?.let { PropertySpec.builder(param.codegenName, it)
            .initializer(param.codegenName)
            .build()
        }
    }
    
    private fun generateDataClassProperty(param: TlParamOptionalKey): PropertySpec {
        val type = ClassName("", param.codegenMultiFlagClassName)
            .copy(nullable = true)

        return PropertySpec.builder(param.codegenMultiFlagFieldName, type)
            .initializer(param.codegenMultiFlagFieldName)
            .build()
    }
    
    private fun generateDataClassParameter(param: TlParam): ParameterSpec? {
        val type = mapTlParamTypeToKotlinType(param.type)
        return type?.let { ParameterSpec.builder(param.codegenName, it)
            .build()
        }
    }

    private fun generateDataClassParameter(param: TlParamOptionalKey): ParameterSpec {
        val type = ClassName("", param.codegenMultiFlagClassName)
            .copy(nullable = true)

        return ParameterSpec.builder(param.codegenMultiFlagFieldName, type).build()
    }

    /* */

    private fun generateDataClassFlagGetters(params: TlParams): List<PropertySpec> {
        return params.list
            .filter { it.type is TlSchemaParamType.Flags }
            .map { generateDataClassFlagGetter(params, it.name) }
    }

    private fun generateDataClassFlagGetter(params: TlParams, flag: String): PropertySpec {
        val statements = mutableMapOf<TlParamOptionalKey, String>()

        for (param in params.list) {
            val optionalKey = param.optionalKey ?: continue
            if (optionalKey.flags != flag) {
                continue
            }
            if (statements.contains(optionalKey)) {
                continue
            }

            val bit = 1U shl optionalKey.num
            if (optionalKey in params.multiFlags.keys) {
                val fn = params.multiFlags[optionalKey]!!.filter { it.type is TlSchemaParamType.Flag.True }
                if (fn.size == 1) {
                    statements.put(optionalKey, "if (${fn.first().codegenName}) result = result or ${bit}U")
                } else {
                    statements.put(optionalKey, "if (${optionalKey.codegenMultiFlagFieldName} != null) result = result or ${bit}U")
                }
            } else {
                when (param.type) {
                    is TlSchemaParamType.Flag.Optional -> {
                        statements.put(optionalKey, "if (${param.codegenName} != null) result = result or ${bit}U")
                    }
                    is TlSchemaParamType.Flag.True -> {
                        statements.put(optionalKey, "if (${param.codegenName}) result = result or ${bit}U")
                    }
                    else -> {}
                }
            }
        }

        val lines = statements.toList().sortedBy { it.first.num }.map { it.second }

        return PropertySpec.builder(flag, com.squareup.kotlinpoet.U_INT)
            .addModifiers(KModifier.INTERNAL)
            .also {
                it.getter(FunSpec.getterBuilder().addCode(buildString {
                    appendLine("var result = 0U")
                    lines.forEach { appendLine(it) }
                    appendLine("return result")
                }).build())
            }
            .build()
    }

    /* */

    private fun generateSerializeFunction(params: TlParams): FunSpec {
        val builder = FunSpec.builder("serializeToStream")
            .addModifiers(KModifier.PUBLIC)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("stream", CLASS_OUTPUT)

        builder.addStatement("stream.writeInt32(MAGIC.toInt())")
        val normalWrites = params.list.mapNotNull {
            if (it.type is TlSchemaParamType.Flag.True) {
                null
            } else if (it.optionalKey in params.multiParamsInOneFlag.keys) {

                generateWriteOptionalMultiParamType("stream", it.optionalKey!!.codegenMultiFlagFieldName, it.codegenName, (it.type as TlSchemaParamType.Flag.Optional).type);
            } else {
                generateWriteStatement("stream", it.codegenName, it.type)
            }


        }
        for (line in normalWrites) {
            builder.addStatement(line)
        }

        return builder.build()
    }

    private fun generateWriteVectorStatement(streamVar: String, paramName: String, type: TlSchemaParamType): String {
        return when (type) {
            is TlSchemaParamType.Primary.Object -> "TlGen_Vector.serialize($streamVar, $paramName)"
            is TlSchemaParamType.Primary.Primitive -> when (type.type) {
                TlSchemaPrimitiveType.INT -> "TlGen_Vector.serializeInt($streamVar, $paramName)"
                TlSchemaPrimitiveType.LONG -> "TlGen_Vector.serializeLong($streamVar, $paramName)"
                TlSchemaPrimitiveType.DOUBLE -> "TlGen_Vector.serializeDouble($streamVar, $paramName)"
                TlSchemaPrimitiveType.STRING -> "TlGen_Vector.serializeString($streamVar, $paramName)"
                TlSchemaPrimitiveType.BYTES -> "TlGen_Vector.serializeBytes($streamVar, $paramName)"
                else -> throw NotImplementedError()
            }

            else -> throw NotImplementedError()
        }
    }

    private fun generateWriteOptionalMultiParamType(streamVar: String, multiflagName: String, paramName: String,  type: TlSchemaParamType): String? {
        return "${multiflagName}?.let { ${generateWriteStatement(streamVar, "it.${paramName}", type)} }"

    }

    private fun generateWriteOptionalType(streamVar: String, paramName: String, type: TlSchemaParamType): String? {
        return when (type) {
            is TlSchemaParamType.Primary.Object -> {
                if (type.name == "Bool") {
                    "${paramName}?.let { ${generateWriteStatement(streamVar, "it", type)} }"
                } else {
                    generateWriteStatement(streamVar, "$paramName?", type)
                }
            }
            else -> "${paramName}?.let { ${generateWriteStatement(streamVar, "it", type)} }"
        }
    }

    private fun generateWriteStatement(streamVar: String, paramName: String, type: TlSchemaParamType): String? {
        return when (type) {
            is TlSchemaParamType.Primary.Primitive -> when (type.type) {
                TlSchemaPrimitiveType.INT -> "$streamVar.writeInt32($paramName)"
                TlSchemaPrimitiveType.LONG -> "$streamVar.writeInt64($paramName)"
                TlSchemaPrimitiveType.DOUBLE -> "$streamVar.writeDouble($paramName)"
                TlSchemaPrimitiveType.STRING -> "$streamVar.writeString($paramName)"
                TlSchemaPrimitiveType.BYTES -> "$streamVar.writeByteArray($paramName.toByteArray())"
                TlSchemaPrimitiveType.INT256 -> "$streamVar.writeBytes($paramName.toByteArray())"
            }

            is TlSchemaParamType.Primary.Object -> {
                if (type.name == "Bool") {
                    "$streamVar.writeBool($paramName)"
                } else {
                    "$paramName.serializeToStream($streamVar)"
                }
            }

            is TlSchemaParamType.Primary.Vector -> generateWriteVectorStatement(streamVar, paramName, type.type) //"$streamVar.writeTLVector($paramName)"

            is TlSchemaParamType.Flag.Optional -> generateWriteOptionalType(streamVar, paramName, type.type)

            is TlSchemaParamType.Flag.True -> null

            is TlSchemaParamType.Flags -> "$streamVar.writeInt32(${paramName}.toInt())"

            is TlSchemaParamType.Primary.X -> "// unknown type for $paramName"
        }
    }



    /* Multi flags */

    private fun generateMultiFlagDataClass(key: TlParamOptionalKey, params: List<TlParam>): TypeSpec {
        val constructorBuilder = FunSpec.constructorBuilder()
        params
            .filter { it.type !is TlSchemaParamType.Flag.True }
            .map(::generateMultiFlagDataClassParameter)
            .forEach(constructorBuilder::addParameter)

        val builder = TypeSpec.classBuilder(key.codegenMultiFlagClassName)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(constructorBuilder.build())

        params
            .filter { it.type !is TlSchemaParamType.Flag.True }
            .map(::generateMultiFlagDataClassProperty)
            .forEach(builder::addProperty)

        /*params
            .filter { it.type is TlParamType.TlFlagTrue }
            .map(::generateMultiFlagTrueGetter)
            .forEach(builder::addProperty)*/

        return builder.build()
    }

    private fun generateMultiFlagTrueGetter(param: TlParam): PropertySpec {
        return PropertySpec.builder(param.codegenName, com.squareup.kotlinpoet.BOOLEAN)
            .initializer("true")
            .build()
    }

    private fun generateMultiFlagDataClassProperty(param: TlParam): PropertySpec {
        val type = mapTlParamTypeToKotlinType(param.type)!!.copy(nullable = false)
        return PropertySpec.builder(param.codegenName, type)
            .initializer(param.codegenName)
            .build()
    }

    private fun generateMultiFlagDataClassParameter(param: TlParam): ParameterSpec {
        val type = mapTlParamTypeToKotlinType(param.type)!!.copy(nullable = false)
        return ParameterSpec.builder(param.codegenName, type).build()
    }



    /* Constructor */

    private fun createConstructorIdProp(constructorId: UInt): PropertySpec {
        val hexLiteral = "0x%08XU".format(constructorId.toLong() and 0xFFFFFFFF)
        return PropertySpec.builder("MAGIC", com.squareup.kotlinpoet.U_INT)
            .addModifiers(KModifier.CONST)
            .initializer(hexLiteral)
            .build()
    }



    /* Utils */

    private fun mapTlParamTypeToKotlinType(type: TlSchemaParamType): TypeName? {
        return when (type) {
            is TlSchemaParamType.Primary.Primitive -> when (type.type) {
                TlSchemaPrimitiveType.INT -> com.squareup.kotlinpoet.INT
                TlSchemaPrimitiveType.LONG -> com.squareup.kotlinpoet.LONG
                TlSchemaPrimitiveType.DOUBLE -> com.squareup.kotlinpoet.DOUBLE
                TlSchemaPrimitiveType.STRING -> com.squareup.kotlinpoet.STRING
                TlSchemaPrimitiveType.BYTES -> com.squareup.kotlinpoet.LIST.parameterizedBy(com.squareup.kotlinpoet.BYTE)
                TlSchemaPrimitiveType.INT256 -> com.squareup.kotlinpoet.LIST.parameterizedBy(com.squareup.kotlinpoet.BYTE)
            }

            is TlSchemaParamType.Primary.Vector -> {
                mapTlParamTypeToKotlinType(type.type)?.let {
                    com.squareup.kotlinpoet.LIST.parameterizedBy(it)
                }
            }

            is TlSchemaParamType.Primary.Object -> {
                if (type.name == "Bool") {
                    com.squareup.kotlinpoet.BOOLEAN
                } else {
                    ClassName("org.telegram.tgnet.model.generated", "TlGen_" + type.name.replace('.', '_'))
                }
            }

            is TlSchemaParamType.Flag.Optional -> mapTlParamTypeToKotlinType(type.type)!!.copy(nullable = true)

            is TlSchemaParamType.Flag.True -> com.squareup.kotlinpoet.BOOLEAN

            is TlSchemaParamType.Primary.X -> ClassName("org.telegram.tgnet.model", "TlGen_Object")
            is TlSchemaParamType.Flags -> null
        }
    }
}