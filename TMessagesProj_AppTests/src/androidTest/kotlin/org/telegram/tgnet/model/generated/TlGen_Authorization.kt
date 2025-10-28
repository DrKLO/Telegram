package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_Authorization : TlGen_Object {
  public data class TL_authorization(
    public val current: Boolean,
    public val official_app: Boolean,
    public val password_pending: Boolean,
    public val encrypted_requests_disabled: Boolean,
    public val call_requests_disabled: Boolean,
    public val unconfirmed: Boolean,
    public val hash: Long,
    public val device_model: String,
    public val platform: String,
    public val system_version: String,
    public val api_id: Int,
    public val app_name: String,
    public val app_version: String,
    public val date_created: Int,
    public val date_active: Int,
    public val ip: String,
    public val country: String,
    public val region: String,
  ) : TlGen_Authorization() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (current) result = result or 1U
        if (official_app) result = result or 2U
        if (password_pending) result = result or 4U
        if (encrypted_requests_disabled) result = result or 8U
        if (call_requests_disabled) result = result or 16U
        if (unconfirmed) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(hash)
      stream.writeString(device_model)
      stream.writeString(platform)
      stream.writeString(system_version)
      stream.writeInt32(api_id)
      stream.writeString(app_name)
      stream.writeString(app_version)
      stream.writeInt32(date_created)
      stream.writeInt32(date_active)
      stream.writeString(ip)
      stream.writeString(country)
      stream.writeString(region)
    }

    public companion object {
      public const val MAGIC: UInt = 0xAD01D61DU
    }
  }
}
