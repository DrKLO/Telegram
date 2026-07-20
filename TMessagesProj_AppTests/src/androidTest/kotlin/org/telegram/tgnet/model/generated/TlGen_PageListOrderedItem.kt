package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageListOrderedItem : TlGen_Object {
  public data class TL_pageListOrderedItemText(
    public val checkbox: Boolean,
    public val checked: Boolean,
    public val num: String?,
    public val text: TlGen_RichText,
    public val `value`: Int?,
    public val type: String?,
  ) : TlGen_PageListOrderedItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (checkbox) result = result or 1U
        if (checked) result = result or 2U
        if (num != null) result = result or 4U
        if (value != null) result = result or 8U
        if (type != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      num?.let { stream.writeString(it) }
      text.serializeToStream(stream)
      value?.let { stream.writeInt32(it) }
      type?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x15031189U
    }
  }

  public data class TL_pageListOrderedItemBlocks(
    public val checkbox: Boolean,
    public val checked: Boolean,
    public val num: String?,
    public val blocks: List<TlGen_PageBlock>,
    public val `value`: Int?,
    public val type: String?,
  ) : TlGen_PageListOrderedItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (checkbox) result = result or 1U
        if (checked) result = result or 2U
        if (num != null) result = result or 4U
        if (value != null) result = result or 8U
        if (type != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      num?.let { stream.writeString(it) }
      TlGen_Vector.serialize(stream, blocks)
      value?.let { stream.writeInt32(it) }
      type?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x8FF2D5F0U
    }
  }

  public data class TL_pageListOrderedItemText_layer226(
    public val num: String,
    public val text: TlGen_RichText,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(num)
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5E068047U
    }
  }

  public data class TL_pageListOrderedItemBlocks_layer226(
    public val num: String,
    public val blocks: List<TlGen_PageBlock>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(num)
      TlGen_Vector.serialize(stream, blocks)
    }

    public companion object {
      public const val MAGIC: UInt = 0x98DD8936U
    }
  }
}
