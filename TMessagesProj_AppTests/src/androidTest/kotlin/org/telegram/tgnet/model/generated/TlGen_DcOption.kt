package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DcOption : TlGen_Object {
  public data class TL_dcOption(
    public val ipv6: Boolean,
    public val media_only: Boolean,
    public val tcpo_only: Boolean,
    public val cdn: Boolean,
    public val static: Boolean,
    public val this_port_only: Boolean,
    public val id: Int,
    public val ip_address: String,
    public val port: Int,
    public val secret: List<Byte>?,
  ) : TlGen_DcOption() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (ipv6) result = result or 1U
        if (media_only) result = result or 2U
        if (tcpo_only) result = result or 4U
        if (cdn) result = result or 8U
        if (static) result = result or 16U
        if (this_port_only) result = result or 32U
        if (secret != null) result = result or 1024U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(id)
      stream.writeString(ip_address)
      stream.writeInt32(port)
      secret?.let { stream.writeByteArray(it.toByteArray()) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x18B7A10DU
    }
  }
}
