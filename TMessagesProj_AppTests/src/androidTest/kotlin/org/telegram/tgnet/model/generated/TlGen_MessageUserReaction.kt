package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageUserReaction : TlGen_Object {
  public data class TL_messageUserReaction_layer137(
    public val user_id: Long,
    public val reaction: String,
  ) : TlGen_MessageUserReaction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(user_id)
      stream.writeString(reaction)
    }

    public companion object {
      public const val MAGIC: UInt = 0x932844FAU
    }
  }
}
