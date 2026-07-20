package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
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

  public data class TL_peerColorCollectible(
    public val collectible_id: Long,
    public val gift_emoji_id: Long,
    public val background_emoji_id: Long,
    public val accent_color: Int,
    public val colors: List<Int>,
    public val dark_accent_color: Int?,
    public val dark_colors: List<Int>?,
  ) : TlGen_PeerColor() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (dark_accent_color != null) result = result or 1U
        if (dark_colors != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(collectible_id)
      stream.writeInt64(gift_emoji_id)
      stream.writeInt64(background_emoji_id)
      stream.writeInt32(accent_color)
      TlGen_Vector.serializeInt(stream, colors)
      dark_accent_color?.let { stream.writeInt32(it) }
      dark_colors?.let { TlGen_Vector.serializeInt(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB9C0639AU
    }
  }

  public data class TL_inputPeerColorCollectible(
    public val collectible_id: Long,
  ) : TlGen_PeerColor() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(collectible_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB8EA86A9U
    }
  }
}
