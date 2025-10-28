package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_Authorization : TlGen_Object {
  public data class TL_auth_authorizationSignUpRequired(
    public val terms_of_service: TlGen_help_TermsOfService?,
  ) : TlGen_auth_Authorization() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (terms_of_service != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      terms_of_service?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x44747E9AU
    }
  }

  public data class TL_auth_authorization(
    public val otherwise_relogin_days: Int?,
    public val tmp_sessions: Int?,
    public val future_auth_token: List<Byte>?,
    public val user: TlGen_User,
  ) : TlGen_auth_Authorization() {
    public val setup_password_required: Boolean = otherwise_relogin_days != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (tmp_sessions != null) result = result or 1U
        if (setup_password_required) result = result or 2U
        if (future_auth_token != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      otherwise_relogin_days?.let { stream.writeInt32(it) }
      tmp_sessions?.let { stream.writeInt32(it) }
      future_auth_token?.let { stream.writeByteArray(it.toByteArray()) }
      user.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2EA2C0D4U
    }
  }
}
