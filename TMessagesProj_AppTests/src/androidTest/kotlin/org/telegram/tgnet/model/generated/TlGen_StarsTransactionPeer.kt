package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_StarsTransactionPeer : TlGen_Object {
  public data object TL_starsTransactionPeerUnsupported : TlGen_StarsTransactionPeer() {
    public const val MAGIC: UInt = 0x95F2BFE4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starsTransactionPeerAppStore : TlGen_StarsTransactionPeer() {
    public const val MAGIC: UInt = 0xB457B375U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starsTransactionPeerPlayMarket : TlGen_StarsTransactionPeer() {
    public const val MAGIC: UInt = 0x7B560A0BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starsTransactionPeerPremiumBot : TlGen_StarsTransactionPeer() {
    public const val MAGIC: UInt = 0x250DBAF8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starsTransactionPeerFragment : TlGen_StarsTransactionPeer() {
    public const val MAGIC: UInt = 0xE92FD902U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_starsTransactionPeer(
    public val peer: TlGen_Peer,
  ) : TlGen_StarsTransactionPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0xD80DA15DU
    }
  }

  public data object TL_starsTransactionPeerAds : TlGen_StarsTransactionPeer() {
    public const val MAGIC: UInt = 0x60682812U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_starsTransactionPeerAPI : TlGen_StarsTransactionPeer() {
    public const val MAGIC: UInt = 0xF9677AADU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
