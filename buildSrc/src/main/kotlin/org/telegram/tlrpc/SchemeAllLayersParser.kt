package org.telegram.tlrpc

import com.example.GenerateSchemeTask
import org.telegram.tlrpc.models.*
import java.io.File
import java.nio.charset.Charset

object SchemeAllLayersParser {
    fun parseAllLayers(resources: File): TlSchemeAllLayers {
        val start = 1
        val end = GenerateSchemeTask.LAYER

        val allConstructors2 = mutableMapOf<TlTypeKey, TlObjectWithLayer>()
        val allMethods2 = mutableMapOf<TlTypeKey, TlObjectWithLayer>()

        var oldScheme: TlSchemeWithMeta? = null

        val allLayersList = mutableListOf<TlSchemeWithMeta>()
        for (i in start .. end) {
            val scheme = SchemeTlValidator.validate(SchemeJsonParser.parse(
                TlSchemeVersion(layer = i, minor = Int.MAX_VALUE),
                File(resources, "$i.json")
                    .readText(charset = Charset.defaultCharset())))

            allLayersList.add(scheme)

            if (!scheme.isValid) {
                println("Warning, scheme " + i + ": not valid")
            }

            if (oldScheme != null) {
                val changelog = SchemeChangelogGen.getChangelog(oldScheme, scheme)
                if (changelog.methods.isEmpty() && changelog.constructors.isEmpty()) {
                    println("Warning, scheme " + i + ": changes not found")
                }

                val types = oldScheme.types.keys - scheme.types.keys
                if (types.isNotEmpty()) {
                    println("Warning, scheme " + i + ": types removed " + types.joinToString(", ") )
                }

            }

            for (constructor in scheme.scheme.constructors2) {
                val p = allConstructors2.getOrDefault(constructor.key, TlObjectWithLayer(tl = constructor, layerFirst = i, layerLast = i))
                allConstructors2.put(constructor.key, TlObjectWithLayer(tl = constructor, layerFirst = p.layerFirst, layerLast = i))
            }

            for (method in scheme.scheme.methods2) {
                val p = allMethods2.getOrDefault(method.key, TlObjectWithLayer(tl = method, layerFirst = i, layerLast = i))
                allMethods2.put(method.key, TlObjectWithLayer(tl = method, layerFirst = p.layerFirst, layerLast = i))
            }

            if (i == end) {
                val changes = SchemeChangelogGen.getChangelog(oldScheme!!, scheme)
                println(changes)
            }
            oldScheme = scheme
        }

        val encryptedScheme = SchemeTlValidator.validate(SchemeJsonParser.parse(
            TlSchemeVersion(layer = 0, minor = Int.MAX_VALUE),
            File(resources, "end-to-end-json.json")
                .readText(charset = Charset.defaultCharset())))

        val encryptedConstructors = mutableMapOf<TlTypeKey, TlObjectWithLayer>()
        for (constructorJ in encryptedScheme.scheme.json.constructors) {

            val i = constructorJ.layer!!
            val constructor = encryptedScheme.scheme.constructors2.find { it.key.constructorId == Integer.parseInt(constructorJ.magic).toUInt() }!!

            val p = encryptedConstructors.getOrDefault(constructor.key, TlObjectWithLayer(tl = constructor, layerFirst = i, layerLast = i))
            encryptedConstructors.put(constructor.key, TlObjectWithLayer(tl = constructor, layerFirst = p.layerFirst, layerLast = i))
        }

        return TlSchemeAllLayers(
            schemes = allLayersList,
            constructorsActual2 = allConstructors2.filter { it.value.layerLast == end }.values.toList(),
            constructorsLegacy2 = allConstructors2.filter { it.value.layerLast != end }.values.toList(),
            methodsActual2 = allMethods2.filter { it.value.layerLast == end }.values.toList(),
            methodsLegacy2 = allMethods2.filter { it.value.layerLast != end }.values.toList(),
            encryptedConstructors = encryptedConstructors.values.toList()
        )
    }
}
