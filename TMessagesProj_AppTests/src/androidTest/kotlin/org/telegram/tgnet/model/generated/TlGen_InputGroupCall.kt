package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputGroupCall : TlGen_Object {
  public data class TL_inputGroupCall(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputGroupCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD8AA840FU
    }
  }

  public data class TL_inputGroupCallSlug(
    public val slug: String,
  ) : TlGen_InputGroupCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFE06823FU
    }
  }

  public data class TL_inputGroupCallInviteMessage(
    public val msg_id: Int,
  ) : TlGen_InputGroupCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8C10603FU
    }
  }
}
