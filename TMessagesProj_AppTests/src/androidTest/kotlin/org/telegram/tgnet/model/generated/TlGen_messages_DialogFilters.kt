package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_DialogFilters : TlGen_Object {
  public data class TL_messages_dialogFilters(
    public val tags_enabled: Boolean,
    public val filters: List<TlGen_DialogFilter>,
  ) : TlGen_messages_DialogFilters() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (tags_enabled) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, filters)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2AD93719U
    }
  }
}
