package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_QuickReply : TlGen_Object {
  public data class TL_quickReply(
    public val shortcut_id: Int,
    public val shortcut: String,
    public val top_message: Int,
    public val count: Int,
  ) : TlGen_QuickReply() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(shortcut_id)
      stream.writeString(shortcut)
      stream.writeInt32(top_message)
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0697102BU
    }
  }
}
