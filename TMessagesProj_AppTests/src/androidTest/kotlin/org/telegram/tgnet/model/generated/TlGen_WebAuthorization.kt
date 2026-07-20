package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_WebAuthorization : TlGen_Object {
  public data class TL_webAuthorization(
    public val hash: Long,
    public val bot_id: Long,
    public val domain: String,
    public val browser: String,
    public val platform: String,
    public val date_created: Int,
    public val date_active: Int,
    public val ip: String,
    public val region: String,
  ) : TlGen_WebAuthorization() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      stream.writeInt64(bot_id)
      stream.writeString(domain)
      stream.writeString(browser)
      stream.writeString(platform)
      stream.writeInt32(date_created)
      stream.writeInt32(date_active)
      stream.writeString(ip)
      stream.writeString(region)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA6F8F452U
    }
  }
}
