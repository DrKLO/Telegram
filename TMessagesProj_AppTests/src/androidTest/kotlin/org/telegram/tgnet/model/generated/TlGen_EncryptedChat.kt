package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_EncryptedChat : TlGen_Object {
  public data class TL_encryptedChatEmpty(
    public val id: Int,
  ) : TlGen_EncryptedChat() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAB7EC0A0U
    }
  }

  public data class TL_encryptedChatDiscarded(
    public val history_deleted: Boolean,
    public val id: Int,
  ) : TlGen_EncryptedChat() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (history_deleted) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E1C7C45U
    }
  }

  public data class TL_encryptedChatWaiting(
    public val id: Int,
    public val access_hash: Long,
    public val date: Int,
    public val admin_id: Long,
    public val participant_id: Long,
  ) : TlGen_EncryptedChat() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt64(admin_id)
      stream.writeInt64(participant_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x66B25953U
    }
  }

  public data class TL_encryptedChatRequested(
    public val folder_id: Int?,
    public val id: Int,
    public val access_hash: Long,
    public val date: Int,
    public val admin_id: Long,
    public val participant_id: Long,
    public val g_a: List<Byte>,
  ) : TlGen_EncryptedChat() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (folder_id != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      folder_id?.let { stream.writeInt32(it) }
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt64(admin_id)
      stream.writeInt64(participant_id)
      stream.writeByteArray(g_a.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x48F1D94CU
    }
  }

  public data class TL_encryptedChat(
    public val id: Int,
    public val access_hash: Long,
    public val date: Int,
    public val admin_id: Long,
    public val participant_id: Long,
    public val g_a_or_b: List<Byte>,
    public val key_fingerprint: Long,
  ) : TlGen_EncryptedChat() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeInt64(access_hash)
      stream.writeInt32(date)
      stream.writeInt64(admin_id)
      stream.writeInt64(participant_id)
      stream.writeByteArray(g_a_or_b.toByteArray())
      stream.writeInt64(key_fingerprint)
    }

    public companion object {
      public const val MAGIC: UInt = 0x61F0D4C7U
    }
  }
}
