package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MissingInvitee : TlGen_Object {
  public data class TL_missingInvitee(
    public val premium_would_allow_invite: Boolean,
    public val premium_required_for_pm: Boolean,
    public val user_id: Long,
  ) : TlGen_MissingInvitee() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (premium_would_allow_invite) result = result or 1U
        if (premium_required_for_pm) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(user_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x628C9224U
    }
  }
}
