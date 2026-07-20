package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_UserStatus : TlGen_Object {
  public data object TL_userStatusEmpty : TlGen_UserStatus() {
    public const val MAGIC: UInt = 0x09D05049U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_userStatusOnline(
    public val expires: Int,
  ) : TlGen_UserStatus() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(expires)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEDB93949U
    }
  }

  public data class TL_userStatusOffline(
    public val was_online: Int,
  ) : TlGen_UserStatus() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(was_online)
    }

    public companion object {
      public const val MAGIC: UInt = 0x008C703FU
    }
  }

  public data class TL_userStatusRecently(
    public val by_me: Boolean,
  ) : TlGen_UserStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (by_me) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x7B197DC8U
    }
  }

  public data class TL_userStatusLastWeek(
    public val by_me: Boolean,
  ) : TlGen_UserStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (by_me) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x541A1D1AU
    }
  }

  public data class TL_userStatusLastMonth(
    public val by_me: Boolean,
  ) : TlGen_UserStatus() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (by_me) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
    }

    public companion object {
      public const val MAGIC: UInt = 0x65899777U
    }
  }

  public data object TL_userStatusRecently_layer171 : TlGen_Object {
    public const val MAGIC: UInt = 0xE26F42F1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_userStatusLastWeek_layer171 : TlGen_Object {
    public const val MAGIC: UInt = 0x07BF09FCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_userStatusLastMonth_layer171 : TlGen_Object {
    public const val MAGIC: UInt = 0x77EBC742U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
