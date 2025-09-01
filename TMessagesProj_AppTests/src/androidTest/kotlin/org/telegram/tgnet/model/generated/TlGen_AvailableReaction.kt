package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_AvailableReaction : TlGen_Object {
  public data class TL_availableReaction(
    public val inactive: Boolean,
    public val premium: Boolean,
    public val reaction: String,
    public val title: String,
    public val static_icon: TlGen_Document,
    public val appear_animation: TlGen_Document,
    public val select_animation: TlGen_Document,
    public val activate_animation: TlGen_Document,
    public val effect_animation: TlGen_Document,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_AvailableReaction() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (inactive) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        if (premium) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(reaction)
      stream.writeString(title)
      static_icon.serializeToStream(stream)
      appear_animation.serializeToStream(stream)
      select_animation.serializeToStream(stream)
      activate_animation.serializeToStream(stream)
      effect_animation.serializeToStream(stream)
      multiflags_1?.let { it.around_animation.serializeToStream(stream) }
      multiflags_1?.let { it.center_icon.serializeToStream(stream) }
    }

    public data class Multiflags_1(
      public val around_animation: TlGen_Document,
      public val center_icon: TlGen_Document,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC077EC01U
    }
  }
}
