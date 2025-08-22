package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DecryptedMessageAction : TlGen_Object {
  public data class TL_decryptedMessageActionSetMessageTTL_layer8(
    public val ttl_seconds: Int,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(ttl_seconds)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA1733AECU
    }
  }

  public data class TL_decryptedMessageActionReadMessages_layer8(
    public val random_ids: List<Long>,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, random_ids)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0C4F40BEU
    }
  }

  public data class TL_decryptedMessageActionDeleteMessages_layer8(
    public val random_ids: List<Long>,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, random_ids)
    }

    public companion object {
      public const val MAGIC: UInt = 0x65614304U
    }
  }

  public data class TL_decryptedMessageActionScreenshotMessages_layer8(
    public val random_ids: List<Long>,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serializeLong(stream, random_ids)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8AC1F475U
    }
  }

  public data object TL_decryptedMessageActionFlushHistory_layer8 : TlGen_DecryptedMessageAction() {
    public const val MAGIC: UInt = 0x6719E45CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_decryptedMessageActionResend_layer17(
    public val start_seq_no: Int,
    public val end_seq_no: Int,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(start_seq_no)
      stream.writeInt32(end_seq_no)
    }

    public companion object {
      public const val MAGIC: UInt = 0x511110B0U
    }
  }

  public data class TL_decryptedMessageActionNotifyLayer_layer17(
    public val layer: Int,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(layer)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF3048883U
    }
  }

  public data class TL_decryptedMessageActionTyping_layer17(
    public val action: TlGen_SendMessageAction,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      action.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xCCB27641U
    }
  }

  public data class TL_decryptedMessageActionRequestKey_layer20(
    public val exchange_id: Long,
    public val g_a: List<Byte>,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(exchange_id)
      stream.writeByteArray(g_a.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xF3C9611BU
    }
  }

  public data class TL_decryptedMessageActionAcceptKey_layer20(
    public val exchange_id: Long,
    public val g_b: List<Byte>,
    public val key_fingerprint: Long,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(exchange_id)
      stream.writeByteArray(g_b.toByteArray())
      stream.writeInt64(key_fingerprint)
    }

    public companion object {
      public const val MAGIC: UInt = 0x6FE1735BU
    }
  }

  public data class TL_decryptedMessageActionAbortKey_layer20(
    public val exchange_id: Long,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(exchange_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xDD05EC6BU
    }
  }

  public data class TL_decryptedMessageActionCommitKey_layer20(
    public val exchange_id: Long,
    public val key_fingerprint: Long,
  ) : TlGen_DecryptedMessageAction() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(exchange_id)
      stream.writeInt64(key_fingerprint)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEC2E0B9BU
    }
  }

  public data object TL_decryptedMessageActionNoop_layer20 : TlGen_DecryptedMessageAction() {
    public const val MAGIC: UInt = 0xA82FDD63U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
