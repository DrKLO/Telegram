package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AvailableEffect : TlGen_Object {
  public data class TL_availableEffect(
    public val premium_required: Boolean,
    public val id: Long,
    public val emoticon: String,
    public val static_icon_id: Long?,
    public val effect_sticker_id: Long,
    public val effect_animation_id: Long?,
  ) : TlGen_AvailableEffect() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (static_icon_id != null) result = result or 1U
        if (effect_animation_id != null) result = result or 2U
        if (premium_required) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(emoticon)
      static_icon_id?.let { stream.writeInt64(it) }
      stream.writeInt64(effect_sticker_id)
      effect_animation_id?.let { stream.writeInt64(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x93C3E27EU
    }
  }
}
