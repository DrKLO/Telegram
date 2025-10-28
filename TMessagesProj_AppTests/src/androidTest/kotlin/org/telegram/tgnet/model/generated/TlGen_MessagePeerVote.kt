package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessagePeerVote : TlGen_Object {
  public data class TL_messagePeerVote(
    public val peer: TlGen_Peer,
    public val option: List<Byte>,
    public val date: Int,
  ) : TlGen_MessagePeerVote() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeByteArray(option.toByteArray())
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0xB6CC2D5CU
    }
  }

  public data class TL_messagePeerVoteInputOption(
    public val peer: TlGen_Peer,
    public val date: Int,
  ) : TlGen_MessagePeerVote() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x74CDA504U
    }
  }

  public data class TL_messagePeerVoteMultiple(
    public val peer: TlGen_Peer,
    public val options: List<List<Byte>>,
    public val date: Int,
  ) : TlGen_MessagePeerVote() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      TlGen_Vector.serializeBytes(stream, options)
      stream.writeInt32(date)
    }

    public companion object {
      public const val MAGIC: UInt = 0x4628F6E6U
    }
  }
}
