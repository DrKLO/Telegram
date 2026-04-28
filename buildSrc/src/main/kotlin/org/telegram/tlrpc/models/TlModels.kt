package org.telegram.tlrpc.models

import com.example.GenerateSchemeTask
import org.telegram.tlrpc.schema.TlSchemaParamType
import org.telegram.tlrpc.schema.TlSchemaJson

data class TlSchemeAllLayers(
    val schemes: List<TlSchemeWithMeta>,

    val constructorsActual2: List<TlObjectWithLayer>,
    val constructorsLegacy2: List<TlObjectWithLayer>,
    val methodsActual2: List<TlObjectWithLayer>,
    val methodsLegacy2: List<TlObjectWithLayer>,
    val encryptedConstructors: List<TlObjectWithLayer>,
) {
    val allConstructors = (
        constructorsActual2.map { it.tl.key.constructorId } +
        constructorsLegacy2.map { it.tl.key.constructorId } +
        methodsActual2.map { it.tl.key.constructorId } +
        methodsLegacy2.map { it.tl.key.constructorId } +
        encryptedConstructors.map { it.tl.key.constructorId }
    ).toSet()
}

data class TlSchemeVersion (
    val layer: Int,
    val minor: Int,
)

data class TlSchemeWithMeta(
    val scheme: TlScheme,
    val isValid: Boolean,
    val types: Map<String, List<TlObject>>,
    val dependenciesDirect: Map<String, Set<String>>,
    val dependenciesTransitive: Map<String, Set<String>>,

    val constructorsByName: Map<TlTypeName, TlObject>,
    val methodsByName: Map<String, TlObject>,
)

data class TlScheme(
    val version: TlSchemeVersion,
    val json: TlSchemaJson,
    val constructors2: List<TlObject>,
    val methods2: List<TlObject>
)



/**/

data class TlObjectWithLayer(
    val tl: TlObject,
    val layerFirst: Int,
    val layerLast: Int,
) {
    val codegenDataClassName: String get() {
        var predicteClassName = "TL_${tl.key.name.predicateName}"
        if (layerLast != GenerateSchemeTask.LAYER) {
            predicteClassName += "_layer${layerLast}"
        }
        return predicteClassName
    }
}

data class TlObject(
    val key: TlTypeKey,
    val params: TlParams
)

data class TlTypeName(
    val type: String,
    val predicate: String
) {
    val predicateName get() = predicate.replace('.', '_')
    val typeName get() = type.replace('.', '_')
}

data class TlTypeKey(
    val name: TlTypeName,
    val constructorId: UInt,
) {

    override fun toString(): String {
        return name.predicate + "#" + constructorId.toString(16).padStart(8, '0') + " = " + name.type
    }
}

/**/


data class TlParamOptionalKey(
    val flags: String,
    val num: Int
) {
    val codegenMultiFlagClassName = "Multi${flags}_${num}"
    val codegenMultiFlagFieldName = "multi${flags}_${num}"
}

data class TlParam(
    val name: String,
    val type: TlSchemaParamType
) {
    val codegenName = name.replace('.', '_')

    val optionalKey: TlParamOptionalKey? = when (type) {
        is TlSchemaParamType.Flag.Optional -> TlParamOptionalKey(type.flag, type.num)
        is TlSchemaParamType.Flag.True -> TlParamOptionalKey(type.flag, type.num)
        else -> null
    }
}

data class TlParams(
    val list: List<TlParam>,
) {
    private val multiFlagsAll: Map<TlParamOptionalKey, List<TlParam>> = list
        .filter { it.optionalKey != null }
        .groupBy { it.optionalKey!! }
        .filterValues { it.size > 1 }

    val isEmpty = list.isEmpty()
    val multiFlags = multiFlagsAll

    val singleParamWithTrueFlag = multiFlags.filterValues { params -> params.count { it.type is TlSchemaParamType.Flag.True } == 1 && params.size == 2 }
    val multiParamsInOneFlag = multiFlags.filterKeys { !singleParamWithTrueFlag.containsKey(it) }
}

