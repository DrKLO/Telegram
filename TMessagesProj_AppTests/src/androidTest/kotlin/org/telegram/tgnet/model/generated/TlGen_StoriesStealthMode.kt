package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StoriesStealthMode : TlGen_Object {
  public data class TL_storiesStealthMode(
    public val active_until_date: Int?,
    public val cooldown_until_date: Int?,
  ) : TlGen_StoriesStealthMode() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (active_until_date != null) result = result or 1U
        if (cooldown_until_date != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      active_until_date?.let { stream.writeInt32(it) }
      cooldown_until_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x712E27FDU
    }
  }
}
