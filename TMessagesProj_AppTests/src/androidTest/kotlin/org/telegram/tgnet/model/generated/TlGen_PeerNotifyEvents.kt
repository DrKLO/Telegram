package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_PeerNotifyEvents : TlGen_Object {
  public data object TL_peerNotifyEventsEmpty_layer78 : TlGen_PeerNotifyEvents() {
    public const val MAGIC: UInt = 0xADD53CB3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_peerNotifyEventsAll_layer78 : TlGen_PeerNotifyEvents() {
    public const val MAGIC: UInt = 0x6D1DED88U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
