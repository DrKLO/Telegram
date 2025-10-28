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

public sealed class TlGen_account_Password : TlGen_Object {
  public data class TL_account_password(
    public val has_recovery: Boolean,
    public val has_secure_values: Boolean,
    public val hint: String?,
    public val email_unconfirmed_pattern: String?,
    public val new_algo: TlGen_PasswordKdfAlgo,
    public val new_secure_algo: TlGen_SecurePasswordKdfAlgo,
    public val secure_random: List<Byte>,
    public val pending_reset_date: Int?,
    public val login_email_pattern: String?,
    public val multiflags_2: Multiflags_2?,
  ) : TlGen_account_Password() {
    public val has_password: Boolean = multiflags_2 != null

    internal val flags: UInt
      get() {
        var result = 0U
        if (has_recovery) result = result or 1U
        if (has_secure_values) result = result or 2U
        if (has_password) result = result or 4U
        if (hint != null) result = result or 8U
        if (email_unconfirmed_pattern != null) result = result or 16U
        if (pending_reset_date != null) result = result or 32U
        if (login_email_pattern != null) result = result or 64U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      multiflags_2?.let { it.current_algo.serializeToStream(stream) }
      multiflags_2?.let { stream.writeByteArray(it.srp_B.toByteArray()) }
      multiflags_2?.let { stream.writeInt64(it.srp_id) }
      hint?.let { stream.writeString(it) }
      email_unconfirmed_pattern?.let { stream.writeString(it) }
      new_algo.serializeToStream(stream)
      new_secure_algo.serializeToStream(stream)
      stream.writeByteArray(secure_random.toByteArray())
      pending_reset_date?.let { stream.writeInt32(it) }
      login_email_pattern?.let { stream.writeString(it) }
    }

    public data class Multiflags_2(
      public val current_algo: TlGen_PasswordKdfAlgo,
      public val srp_B: List<Byte>,
      public val srp_id: Long,
    )

    public companion object {
      public const val MAGIC: UInt = 0x957B50FBU
    }
  }
}
