package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPageListOrderedItem : TlGen_Object {
  public data class TL_inputPageListOrderedItemText(
    public val checkbox: Boolean,
    public val checked: Boolean,
    public val text: TlGen_RichText,
    public val `value`: Int?,
    public val type: String?,
  ) : TlGen_InputPageListOrderedItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (checkbox) result = result or 1U
        if (checked) result = result or 2U
        if (value != null) result = result or 4U
        if (type != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      text.serializeToStream(stream)
      value?.let { stream.writeInt32(it) }
      type?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x9BB48F20U
    }
  }

  public data class TL_inputPageListOrderedItemBlocks(
    public val checkbox: Boolean,
    public val checked: Boolean,
    public val blocks: List<TlGen_PageBlock>,
    public val `value`: Int?,
    public val type: String?,
  ) : TlGen_InputPageListOrderedItem() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (checkbox) result = result or 1U
        if (checked) result = result or 2U
        if (value != null) result = result or 4U
        if (type != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, blocks)
      value?.let { stream.writeInt32(it) }
      type?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x7D7B3802U
    }
  }
}
