package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PeerColor : TlGen_Object {
  public data class TL_peerColor(
    public val color: Int?,
    public val background_emoji_id: Long?,
  ) : TlGen_PeerColor() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (color != null) result = result or 1U
        if (background_emoji_id != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      color?.let { stream.writeInt32(it) }
      background_emoji_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB54B5ACFU
    }
  }
}
