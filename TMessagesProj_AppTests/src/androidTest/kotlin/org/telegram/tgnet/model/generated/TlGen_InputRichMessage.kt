package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputRichMessage : TlGen_Object {
  public data class TL_inputRichMessage(
    public val rtl: Boolean,
    public val noautolink: Boolean,
    public val blocks: List<TlGen_PageBlock>,
    public val photos: List<TlGen_InputPhoto>?,
    public val documents: List<TlGen_InputDocument>?,
    public val users: List<TlGen_InputUser>?,
  ) : TlGen_InputRichMessage() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (rtl) result = result or 1U
        if (noautolink) result = result or 2U
        if (photos != null) result = result or 4U
        if (documents != null) result = result or 8U
        if (users != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, blocks)
      photos?.let { TlGen_Vector.serialize(stream, it) }
      documents?.let { TlGen_Vector.serialize(stream, it) }
      users?.let { TlGen_Vector.serialize(stream, it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xE4C449FCU
    }
  }
}
