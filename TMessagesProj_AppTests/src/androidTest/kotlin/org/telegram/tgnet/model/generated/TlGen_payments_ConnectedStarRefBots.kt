package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_payments_ConnectedStarRefBots : TlGen_Object {
  public data class TL_payments_connectedStarRefBots(
    public val count: Int,
    public val connected_bots: List<TlGen_ConnectedBotStarRef>,
    public val users: List<TlGen_User>,
  ) : TlGen_payments_ConnectedStarRefBots() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, connected_bots)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x98D5EA1DU
    }
  }
}
