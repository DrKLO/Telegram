package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_PhoneCallProtocol : TlGen_Object {
  public data class TL_phoneCallProtocol(
    public val udp_p2p: Boolean,
    public val udp_reflector: Boolean,
    public val min_layer: Int,
    public val max_layer: Int,
    public val library_versions: List<String>,
  ) : TlGen_PhoneCallProtocol() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (udp_p2p) result = result or 1U
        if (udp_reflector) result = result or 2U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt32(min_layer)
      stream.writeInt32(max_layer)
      TlGen_Vector.serializeString(stream, library_versions)
    }

    public companion object {
      public const val MAGIC: UInt = 0xFC878FC8U
    }
  }
}
