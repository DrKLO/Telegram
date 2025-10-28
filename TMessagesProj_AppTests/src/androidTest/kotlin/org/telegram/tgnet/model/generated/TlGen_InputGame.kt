package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputGame : TlGen_Object {
  public data class TL_inputGameID(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputGame() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x032C3E77U
    }
  }

  public data class TL_inputGameShortName(
    public val bot_id: TlGen_InputUser,
    public val short_name: String,
  ) : TlGen_InputGame() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      bot_id.serializeToStream(stream)
      stream.writeString(short_name)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC331E80AU
    }
  }
}
