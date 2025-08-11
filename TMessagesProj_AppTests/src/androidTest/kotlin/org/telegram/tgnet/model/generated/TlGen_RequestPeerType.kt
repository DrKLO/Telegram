package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_RequestPeerType : TlGen_Object {
  public data class TL_requestPeerTypeUser(
    public val bot: Boolean?,
    public val premium: Boolean?,
  ) : TlGen_RequestPeerType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (bot != null) result = result or 1U
        if (premium != null) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      bot?.let { stream.writeBool(it) }
      premium?.let { stream.writeBool(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x5F3B8A00U
    }
  }

  public data class TL_requestPeerTypeChat(
    public val creator: Boolean,
    public val bot_participant: Boolean,
    public val has_username: Boolean?,
    public val forum: Boolean?,
    public val user_admin_rights: TlGen_ChatAdminRights?,
    public val bot_admin_rights: TlGen_ChatAdminRights?,
  ) : TlGen_RequestPeerType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (user_admin_rights != null) result = result or 2U
        if (bot_admin_rights != null) result = result or 4U
        if (has_username != null) result = result or 8U
        if (forum != null) result = result or 16U
        if (bot_participant) result = result or 32U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      has_username?.let { stream.writeBool(it) }
      forum?.let { stream.writeBool(it) }
      user_admin_rights?.serializeToStream(stream)
      bot_admin_rights?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC9F06E1BU
    }
  }

  public data class TL_requestPeerTypeBroadcast(
    public val creator: Boolean,
    public val has_username: Boolean?,
    public val user_admin_rights: TlGen_ChatAdminRights?,
    public val bot_admin_rights: TlGen_ChatAdminRights?,
  ) : TlGen_RequestPeerType() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (creator) result = result or 1U
        if (user_admin_rights != null) result = result or 2U
        if (bot_admin_rights != null) result = result or 4U
        if (has_username != null) result = result or 8U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      has_username?.let { stream.writeBool(it) }
      user_admin_rights?.serializeToStream(stream)
      bot_admin_rights?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x339BEF6CU
    }
  }
}
