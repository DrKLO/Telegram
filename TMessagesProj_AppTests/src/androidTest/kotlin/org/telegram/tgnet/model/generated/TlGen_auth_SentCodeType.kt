package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_SentCodeType : TlGen_Object {
  public data class TL_auth_sentCodeTypeApp(
    public val length: Int,
  ) : TlGen_auth_SentCodeType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3DBB5986U
    }
  }

  public data class TL_auth_sentCodeTypeSms(
    public val length: Int,
  ) : TlGen_auth_SentCodeType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC000BBA2U
    }
  }

  public data class TL_auth_sentCodeTypeCall(
    public val length: Int,
  ) : TlGen_auth_SentCodeType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5353E5A7U
    }
  }

  public data class TL_auth_sentCodeTypeFlashCall(
    public val pattern: String,
  ) : TlGen_auth_SentCodeType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(pattern)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAB03C6D9U
    }
  }

  public data class TL_auth_sentCodeTypeMissedCall(
    public val prefix: String,
    public val length: Int,
  ) : TlGen_auth_SentCodeType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(prefix)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0x82006484U
    }
  }

  public data class TL_auth_sentCodeTypeSetUpEmailRequired(
    public val apple_signin_allowed: Boolean,
    public val google_signin_allowed: Boolean,
  ) : TlGen_auth_SentCodeType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (apple_signin_allowed) result = result or 1U
        if (google_signin_allowed) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0xA5491DEAU
    }
  }

  public data class TL_auth_sentCodeTypeFragmentSms(
    public val url: String,
    public val length: Int,
  ) : TlGen_auth_SentCodeType() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeInt32(length)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD9565C39U
    }
  }

  public data class TL_auth_sentCodeTypeEmailCode(
    public val apple_signin_allowed: Boolean,
    public val google_signin_allowed: Boolean,
    public val email_pattern: String,
    public val length: Int,
    public val reset_available_period: Int?,
    public val reset_pending_date: Int?,
  ) : TlGen_auth_SentCodeType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (apple_signin_allowed) result = result or 1U
        if (google_signin_allowed) result = result or 2U
        if (reset_available_period != null) result = result or 8U
        if (reset_pending_date != null) result = result or 16U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeString(email_pattern)
      stream.writeInt32(length)
      reset_available_period?.let { stream.writeInt32(it) }
      reset_pending_date?.let { stream.writeInt32(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xF450F59BU
    }
  }

  public data class TL_auth_sentCodeTypeSmsWord(
    public val beginning: String?,
  ) : TlGen_auth_SentCodeType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (beginning != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      beginning?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xA416AC81U
    }
  }

  public data class TL_auth_sentCodeTypeSmsPhrase(
    public val beginning: String?,
  ) : TlGen_auth_SentCodeType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (beginning != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      beginning?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xB37794AFU
    }
  }

  public data class TL_auth_sentCodeTypeFirebaseSms(
    public val nonce: List<Byte>?,
    public val length: Int,
    public val multiflags_2: Multiflags_2?,
    public val multiflags_1: Multiflags_1?,
  ) : TlGen_auth_SentCodeType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (nonce != null) result = result or 1U
        if (multiflags_1 != null) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      nonce?.let { stream.writeByteArray(it.toByteArray()) }
      multiflags_2?.let { stream.writeInt64(it.play_integrity_project_id) }
      multiflags_2?.let { stream.writeByteArray(it.play_integrity_nonce.toByteArray()) }
      multiflags_1?.let { stream.writeString(it.receipt) }
      multiflags_1?.let { stream.writeInt32(it.push_timeout) }
      stream.writeInt32(length)
    }

    public data class Multiflags_2(
      public val play_integrity_project_id: Long,
      public val play_integrity_nonce: List<Byte>,
    )

    public data class Multiflags_1(
      public val receipt: String,
      public val push_timeout: Int,
    )

    public companion object {
      public const val MAGIC: UInt = 0x009FD736U
    }
  }
}
