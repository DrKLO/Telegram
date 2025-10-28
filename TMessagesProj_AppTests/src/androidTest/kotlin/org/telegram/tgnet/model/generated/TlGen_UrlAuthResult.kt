package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_UrlAuthResult : TlGen_Object {
  public data class TL_urlAuthResultRequest(
    public val request_write_access: Boolean,
    public val bot: TlGen_User,
    public val domain: String,
  ) : TlGen_UrlAuthResult() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (request_write_access) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      bot.serializeToStream(stream)
      stream.writeString(domain)
    }

    public companion object {
      public const val MAGIC: UInt = 0x92D33A0EU
    }
  }

  public data class TL_urlAuthResultAccepted(
    public val url: String,
  ) : TlGen_UrlAuthResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
    }

    public companion object {
      public const val MAGIC: UInt = 0x8F8C0E4EU
    }
  }

  public data object TL_urlAuthResultDefault : TlGen_UrlAuthResult() {
    public const val MAGIC: UInt = 0xA9D6DB1FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
