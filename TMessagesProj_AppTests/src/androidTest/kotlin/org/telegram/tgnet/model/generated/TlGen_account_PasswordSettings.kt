package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_PasswordSettings : TlGen_Object {
  public data class TL_account_passwordSettings(
    public val email: String?,
    public val secure_settings: TlGen_SecureSecretSettings?,
  ) : TlGen_account_PasswordSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (email != null) result = result or 1U
        if (secure_settings != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      email?.let { stream.writeString(it) }
      secure_settings?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9A5C33E5U
    }
  }
}
