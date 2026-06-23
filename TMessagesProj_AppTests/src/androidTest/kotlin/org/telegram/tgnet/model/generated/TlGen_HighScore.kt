package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.Long
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_HighScore : TlGen_Object {
  public data class TL_highScore(
    public val pos: Int,
    public val user_id: Long,
    public val score: Int,
  ) : TlGen_HighScore() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(pos)
      stream.writeInt64(user_id)
      stream.writeInt32(score)
    }

    public companion object {
      public const val MAGIC: UInt = 0x73A379EBU
    }
  }
}
