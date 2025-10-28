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

public sealed class TlGen_PhoneConnection : TlGen_Object {
  public data class TL_phoneConnectionWebrtc(
    public val turn: Boolean,
    public val stun: Boolean,
    public val id: Long,
    public val ip: String,
    public val ipv6: String,
    public val port: Int,
    public val username: String,
    public val password: String,
  ) : TlGen_PhoneConnection() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (turn) result = result or 1U
        if (stun) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(ip)
      stream.writeString(ipv6)
      stream.writeInt32(port)
      stream.writeString(username)
      stream.writeString(password)
    }

    public companion object {
      public const val MAGIC: UInt = 0x635FE375U
    }
  }

  public data class TL_phoneConnection(
    public val tcp: Boolean,
    public val id: Long,
    public val ip: String,
    public val ipv6: String,
    public val port: Int,
    public val peer_tag: List<Byte>,
  ) : TlGen_PhoneConnection() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (tcp) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeString(ip)
      stream.writeString(ipv6)
      stream.writeInt32(port)
      stream.writeByteArray(peer_tag.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x9CC123C7U
    }
  }
}
