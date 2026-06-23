package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputBotInlineMessageID : TlGen_Object {
  public data class TL_inputBotInlineMessageID(
    public val dc_id: Int,
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputBotInlineMessageID() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(dc_id)
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x890C3D89U
    }
  }
}
