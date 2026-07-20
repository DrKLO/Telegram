package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SendAsPeer : TlGen_Object {
  public data class TL_sendAsPeer(
    public val premium_required: Boolean,
    public val peer: TlGen_Peer,
  ) : TlGen_SendAsPeer() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (premium_required) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB81C7034U
    }
  }
}
