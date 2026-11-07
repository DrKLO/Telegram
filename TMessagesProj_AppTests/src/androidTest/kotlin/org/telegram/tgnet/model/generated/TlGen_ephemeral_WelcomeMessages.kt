package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ephemeral_WelcomeMessages : TlGen_Object {
  public data object TL_ephemeral_welcomeMessagesNotModified : TlGen_ephemeral_WelcomeMessages() {
    public const val MAGIC: UInt = 0x59FFDB31U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_ephemeral_welcomeMessages(
    public val hash: Long,
    public val messages: List<TlGen_EphemeralMessage>,
  ) : TlGen_ephemeral_WelcomeMessages() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, messages)
    }

    public companion object {
      public const val MAGIC: UInt = 0x104FC872U
    }
  }
}
