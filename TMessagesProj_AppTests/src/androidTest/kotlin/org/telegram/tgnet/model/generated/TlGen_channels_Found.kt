package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_channels_Found : TlGen_Object {
  public data class TL_channels_found(
    public val results: List<TlGen_Peer>,
    public val chats: List<TlGen_Chat>,
    public val users: List<TlGen_User>,
    public val next_offset: String?,
  ) : TlGen_channels_Found() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (next_offset != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, results)
      TlGen_Vector.serialize(stream, chats)
      TlGen_Vector.serialize(stream, users)
      next_offset?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x3128C4BCU
    }
  }
}
