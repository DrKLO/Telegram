package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PageListItem : TlGen_Object {
  public data class TL_pageListItemText(
    public val checkbox: Boolean,
    public val checked: Boolean,
    public val text: TlGen_RichText,
  ) : TlGen_PageListItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (checkbox) result = result or 1U
        if (checked) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2F58683CU
    }
  }

  public data class TL_pageListItemBlocks(
    public val checkbox: Boolean,
    public val checked: Boolean,
    public val blocks: List<TlGen_PageBlock>,
  ) : TlGen_PageListItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (checkbox) result = result or 1U
        if (checked) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, blocks)
    }

    public companion object {
      public const val MAGIC: UInt = 0x63CA67AAU
    }
  }

  public data class TL_pageListItemText_layer226(
    public val text: TlGen_RichText,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      text.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB92FB6CDU
    }
  }

  public data class TL_pageListItemBlocks_layer226(
    public val blocks: List<TlGen_PageBlock>,
  ) : TlGen_Object {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, blocks)
    }

    public companion object {
      public const val MAGIC: UInt = 0x25E073FCU
    }
  }
}
