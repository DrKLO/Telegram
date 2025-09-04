package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_PasswordInputSettings : TlGen_Object {
  public data class TL_account_passwordInputSettings(
    public val email: String?,
    public val new_secure_settings: TlGen_SecureSecretSettings?,
    public val multiflags_0: Multiflags_0?,
  ) : TlGen_account_PasswordInputSettings() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (multiflags_0 != null) result = result or 1U
        if (email != null) result = result or 2U
        if (new_secure_settings != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      multiflags_0?.let { it.new_algo.serializeToStream(stream) }
      multiflags_0?.let { stream.writeByteArray(it.new_password_hash.toByteArray()) }
      multiflags_0?.let { stream.writeString(it.hint) }
      email?.let { stream.writeString(it) }
      new_secure_settings?.serializeToStream(stream)
    }

    public data class Multiflags_0(
      public val new_algo: TlGen_PasswordKdfAlgo,
      public val new_password_hash: List<Byte>,
      public val hint: String,
    )

    public companion object {
      public const val MAGIC: UInt = 0xC23727C9U
    }
  }
}
