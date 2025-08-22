package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MyBoost : TlGen_Object {
  public data class TL_myBoost(
    public val slot: Int,
    public val peer: TlGen_Peer?,
    public val date: Int,
    public val expires: Int,
    public val cooldown_until_date: Int?,
  ) : TlGen_MyBoost() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (peer != null) result = result or 1U
        if (cooldown_until_date != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(slot)
      peer?.serializeToStream(stream)
      stream.writeInt32(date)
      stream.writeInt32(expires)
      cooldown_until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xC448415CU
    }
  }
}
