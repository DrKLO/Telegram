package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_MessageEditData : TlGen_Object {
  public data class TL_messages_messageEditData(
    public val caption: Boolean,
  ) : TlGen_messages_MessageEditData() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (caption) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x26B5DDE6U
    }
  }
}
