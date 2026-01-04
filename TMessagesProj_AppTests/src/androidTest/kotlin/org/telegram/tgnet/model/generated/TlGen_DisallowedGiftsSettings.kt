package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DisallowedGiftsSettings : TlGen_Object {
  public data class TL_disallowedGiftsSettings(
    public val disallow_unlimited_stargifts: Boolean,
    public val disallow_limited_stargifts: Boolean,
    public val disallow_unique_stargifts: Boolean,
    public val disallow_premium_gifts: Boolean,
    public val disallow_stargifts_from_channels: Boolean,
  ) : TlGen_DisallowedGiftsSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (disallow_unlimited_stargifts) result = result or 1U
        if (disallow_limited_stargifts) result = result or 2U
        if (disallow_unique_stargifts) result = result or 4U
        if (disallow_premium_gifts) result = result or 8U
        if (disallow_stargifts_from_channels) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x71F276C4U
    }
  }
}
