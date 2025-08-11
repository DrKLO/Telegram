package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_AuthorizationForm : TlGen_Object {
  public data class TL_account_authorizationForm(
    public val required_types: List<TlGen_SecureRequiredType>,
    public val values: List<TlGen_SecureValue>,
    public val errors: List<TlGen_SecureValueError>,
    public val users: List<TlGen_User>,
    public val privacy_policy_url: String?,
  ) : TlGen_account_AuthorizationForm() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (privacy_policy_url != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      TlGen_Vector.serialize(stream, required_types)
      TlGen_Vector.serialize(stream, values)
      TlGen_Vector.serialize(stream, errors)
      TlGen_Vector.serialize(stream, users)
      privacy_policy_url?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0xAD2E1CD8U
    }
  }
}
