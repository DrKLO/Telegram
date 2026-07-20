package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputStickerSet : TlGen_Object {
  public data object TL_inputStickerSetEmpty : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0xFFB62B95U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputStickerSetID(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputStickerSet() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9DE7A269U
    }
  }

  public data class TL_inputStickerSetShortName(
    public val short_name: String,
  ) : TlGen_InputStickerSet() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(short_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0x861CC8A0U
    }
  }

  public data object TL_inputStickerSetAnimatedEmoji : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0x028703C8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputStickerSetDice(
    public val emoticon: String,
  ) : TlGen_InputStickerSet() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(emoticon)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE67F520EU
    }
  }

  public data object TL_inputStickerSetPremiumGifts : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0xC88B3B02U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputStickerSetEmojiGenericAnimations : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0x04C4D4CEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputStickerSetEmojiDefaultStatuses : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0x29D0F5EEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputStickerSetEmojiDefaultTopicIcons : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0x44C1F8E9U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputStickerSetEmojiChannelDefaultStatuses : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0x49748553U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputStickerSetTonGifts : TlGen_InputStickerSet() {
    public const val MAGIC: UInt = 0x1CF671A0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_inputStickerSetDice_layer111 : TlGen_Object {
    public const val MAGIC: UInt = 0x79E21A53U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
