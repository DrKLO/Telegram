package org.telegram.tlrpc.schema

data class TlSchemaObject(
    val magic: UInt,
    val name: String,
    val type: String,
    val params: List<TlSchemaParam>,
    val layer: Int?
) {
    companion object {
        fun from(json: TlSchemaJson.JsonTlObject): TlSchemaObject {
            val magic = Integer.parseInt(json.magic).toUInt()

            val flags = json.params.filter { it.type == "#" }.map { it.name }.toSet()
            val params = json.params.map { it -> TlSchemaParam(
                name = it.name,
                type = TlSchemaParamType.parse(flags, it.type))
            }

            return TlSchemaObject(
                magic = magic,
                name = json.name,
                type = json.type,
                params = params,
                layer = json.layer
            )
        }
    }
}