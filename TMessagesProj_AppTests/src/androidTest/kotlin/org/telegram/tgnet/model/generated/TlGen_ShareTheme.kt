package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ShareTheme : TlGen_Object {
  public data object TL_shareThemeDefault : TlGen_ShareTheme() {
    public const val MAGIC: UInt = 0x95ACE84FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_shareThemeEmojiBackdrop(
    public val backdrop_id: Int?,
    public val emojis: List<TlGen_Reaction>?,
  ) : TlGen_ShareTheme() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (backdrop_id != null) result = result or 1U
        if (emojis != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      backdrop_id?.let { stream.writeInt32(it) }
      emojis?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x64B334E1U
    }
  }

  public data class TL_shareThemeGift(
    public val stargift: TlGen_StarGift,
  ) : TlGen_ShareTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stargift.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEBECE66DU
    }
  }

  public data class TL_inputShareThemeGift(
    public val stargift: TlGen_InputSavedStarGift,
  ) : TlGen_ShareTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stargift.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3FB92B5BU
    }
  }
}
