package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputCollectible : TlGen_Object {
  public data class TL_inputCollectibleUsername(
    public val username: String,
  ) : TlGen_InputCollectible() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(username)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE39460A9U
    }
  }

  public data class TL_inputCollectiblePhone(
    public val phone: String,
  ) : TlGen_InputCollectible() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(phone)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA2E214A4U
    }
  }
}
