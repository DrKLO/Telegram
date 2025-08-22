package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_SentEncryptedMessage : TlGen_Object {
  public data class TL_messages_sentEncryptedMessage(
    public val date: Int,
  ) : TlGen_messages_SentEncryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x560F8935U
    }
  }

  public data class TL_messages_sentEncryptedFile(
    public val date: Int,
    public val `file`: TlGen_EncryptedFile,
  ) : TlGen_messages_SentEncryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(date)
      file.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9493FF32U
    }
  }
}
