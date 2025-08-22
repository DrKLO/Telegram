package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EncryptedMessage : TlGen_Object {
  public data class TL_encryptedMessage(
    public val random_id: Long,
    public val chat_id: Int,
    public val date: Int,
    public val bytes: List<Byte>,
    public val `file`: TlGen_EncryptedFile,
  ) : TlGen_EncryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(random_id)
      stream.writeInt32(chat_id)
      stream.writeInt32(date)
      stream.writeByteArray(bytes.toByteArray())
      file.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xED18C118U
    }
  }

  public data class TL_encryptedMessageService(
    public val random_id: Long,
    public val chat_id: Int,
    public val date: Int,
    public val bytes: List<Byte>,
  ) : TlGen_EncryptedMessage() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(random_id)
      stream.writeInt32(chat_id)
      stream.writeInt32(date)
      stream.writeByteArray(bytes.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x23734B06U
    }
  }
}
