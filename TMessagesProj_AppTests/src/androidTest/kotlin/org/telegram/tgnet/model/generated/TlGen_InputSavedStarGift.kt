package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputSavedStarGift : TlGen_Object {
  public data class TL_inputSavedStarGiftUser(
    public val msg_id: Int,
  ) : TlGen_InputSavedStarGift() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(msg_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0x69279795U
    }
  }

  public data class TL_inputSavedStarGiftChat(
    public val peer: TlGen_InputPeer,
    public val saved_id: Long,
  ) : TlGen_InputSavedStarGift() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      peer.serializeToStream(stream)
      stream.writeInt64(saved_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF101AA7FU
    }
  }

  public data class TL_inputSavedStarGiftSlug(
    public val slug: String,
  ) : TlGen_InputSavedStarGift() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2085C238U
    }
  }
}
