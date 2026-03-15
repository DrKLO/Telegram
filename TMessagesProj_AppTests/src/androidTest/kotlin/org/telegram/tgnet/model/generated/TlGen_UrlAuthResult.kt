package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_UrlAuthResult : TlGen_Object {
  public data object TL_urlAuthResultDefault : TlGen_UrlAuthResult() {
    public const val MAGIC: UInt = 0xA9D6DB1FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_urlAuthResultAccepted(
    public val url: String?,
  ) : TlGen_UrlAuthResult() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (url != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      url?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x623A8FA0U
    }
  }

  public data class TL_urlAuthResultRequest(
    public val request_write_access: Boolean,
    public val request_phone_number: Boolean,
    public val match_codes_first: Boolean,
    public val bot: TlGen_User,
    public val domain: String,
    public val match_codes: List<String>?,
    public val user_id_hint: Long?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_UrlAuthResult() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (request_write_access) result = result or 1U
        if (request_phone_number) result = result or 2U
        if (multiflags_2 != null) result = result or 4U
        if (match_codes != null) result = result or 8U
        if (user_id_hint != null) result = result or 16U
        if (match_codes_first) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      bot.serializeToStream(stream)
      stream.writeString(domain)
      multiflags_2?.let { stream.writeString(it.browser) }
      multiflags_2?.let { stream.writeString(it.platform) }
      multiflags_2?.let { stream.writeString(it.ip) }
      multiflags_2?.let { stream.writeString(it.region) }
      match_codes?.let { TlGen_Vector.serializeString(stream, it) }
      user_id_hint?.let { stream.writeInt64(it) }
    }

    public data class Multiflags_2(
      public val browser: String,
      public val platform: String,
      public val ip: String,
      public val region: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0xF8F8EB1EU
    }
  }
}
