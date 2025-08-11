package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatAdminWithInvites : TlGen_Object {
  public data class TL_chatAdminWithInvites(
    public val admin_id: Long,
    public val invites_count: Int,
    public val revoked_invites_count: Int,
  ) : TlGen_ChatAdminWithInvites() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(admin_id)
      stream.writeInt32(invites_count)
      stream.writeInt32(revoked_invites_count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF2ECEF23U
    }
  }
}
