package org.telegram.tlrpc.schema

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.nio.charset.Charset

object TlSchemaJsonParser {
    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val adapter by lazy {
        moshi.adapter(TlSchemaJson::class.java)
    }

    fun parse(json: String): TlSchemaJson {
        return adapter.fromJson(json)!!
    }


    fun parse(file: File): TlSchema {
        return TlSchema.from(parse(file.readText(charset = Charset.defaultCharset())))
    }

    fun parse(directory: File, layer: Int): TlSchemaFull {
        val schema = parse(File(directory, "$layer.json"))
        val encrypted = parse(File(directory, "end-to-end-json.json"))

        val history = mutableMapOf<Int, TlSchema>()

        for (i in 1 until layer) {
            history.put(i,parse(File(directory, "$i.json")))
        }

        return TlSchemaFull(
            schema = schema,
            encrypted = encrypted,
            history = history,
            layer = layer
        )
    }

}