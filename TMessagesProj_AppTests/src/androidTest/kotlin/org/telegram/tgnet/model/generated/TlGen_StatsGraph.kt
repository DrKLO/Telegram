package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StatsGraph : TlGen_Object {
  public data class TL_statsGraphAsync(
    public val token: String,
  ) : TlGen_StatsGraph() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(token)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4A27EB2DU
    }
  }

  public data class TL_statsGraphError(
    public val error: String,
  ) : TlGen_StatsGraph() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(error)
    }

    public companion object {
      public const val MAGIC: UInt = 0xBEDC9822U
    }
  }

  public data class TL_statsGraph(
    public val json: TlGen_DataJSON,
    public val zoom_token: String?,
  ) : TlGen_StatsGraph() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (zoom_token != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      json.serializeToStream(stream)
      zoom_token?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x8EA464B6U
    }
  }
}
