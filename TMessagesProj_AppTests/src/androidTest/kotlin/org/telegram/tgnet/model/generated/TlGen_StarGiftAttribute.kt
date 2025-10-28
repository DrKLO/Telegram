package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarGiftAttribute : TlGen_Object {
  public data class TL_starGiftAttributeModel(
    public val name: String,
    public val document: TlGen_Document,
    public val rarity_permille: Int,
  ) : TlGen_StarGiftAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(name)
      document.serializeToStream(stream)
      stream.writeInt32(rarity_permille)
    }

    public companion object {
      public const val MAGIC: UInt = 0x39D99013U
    }
  }

  public data class TL_starGiftAttributePattern(
    public val name: String,
    public val document: TlGen_Document,
    public val rarity_permille: Int,
  ) : TlGen_StarGiftAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(name)
      document.serializeToStream(stream)
      stream.writeInt32(rarity_permille)
    }

    public companion object {
      public const val MAGIC: UInt = 0x13ACFF19U
    }
  }

  public data class TL_starGiftAttributeOriginalDetails(
    public val sender_id: TlGen_Peer?,
    public val recipient_id: TlGen_Peer,
    public val date: Int,
    public val message: TlGen_TextWithEntities?,
  ) : TlGen_StarGiftAttribute() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (sender_id != null) result = result or 1U
        if (message != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      sender_id?.serializeToStream(stream)
      recipient_id.serializeToStream(stream)
      stream.writeInt32(date)
      message?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE0BFF26CU
    }
  }

  public data class TL_starGiftAttributeBackdrop(
    public val name: String,
    public val backdrop_id: Int,
    public val center_color: Int,
    public val edge_color: Int,
    public val pattern_color: Int,
    public val text_color: Int,
    public val rarity_permille: Int,
  ) : TlGen_StarGiftAttribute() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(name)
      stream.writeInt32(backdrop_id)
      stream.writeInt32(center_color)
      stream.writeInt32(edge_color)
      stream.writeInt32(pattern_color)
      stream.writeInt32(text_color)
      stream.writeInt32(rarity_permille)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD93D859CU
    }
  }

  public data class TL_starGiftAttributeBackdrop_layer202(
    public val name: String,
    public val center_color: Int,
    public val edge_color: Int,
    public val pattern_color: Int,
    public val text_color: Int,
    public val rarity_permille: Int,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(name)
      stream.writeInt32(center_color)
      stream.writeInt32(edge_color)
      stream.writeInt32(pattern_color)
      stream.writeInt32(text_color)
      stream.writeInt32(rarity_permille)
    }

    public companion object {
      public const val MAGIC: UInt = 0x94271762U
    }
  }

  public data class TL_starGiftAttributeOriginalDetails_layer197(
    public val sender_id: Long?,
    public val recipient_id: Long,
    public val date: Int,
    public val message: TlGen_TextWithEntities?,
  ) : TlGen_Object {
    internal val flags: UInt
      get() {
        var result = 0U
        if (sender_id != null) result = result or 1U
        if (message != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      sender_id?.let { stream.writeInt64(it) }
      stream.writeInt64(recipient_id)
      stream.writeInt32(date)
      message?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC02C4F4BU
    }
  }
}
