package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_ConnectedBots : TlGen_Object {
  public data class TL_account_connectedBots(
    public val connected_bots: List<TlGen_ConnectedBot>,
    public val users: List<TlGen_User>,
  ) : TlGen_account_ConnectedBots() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, connected_bots)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x17D7F87BU
    }
  }
}
