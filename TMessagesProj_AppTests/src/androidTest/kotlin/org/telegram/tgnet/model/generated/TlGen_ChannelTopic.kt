package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChannelTopic : TlGen_Object {
  public data class TL_channelTopic(
    public val id: Int,
    public val title: String,
  ) : TlGen_ChannelTopic() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(id)
      stream.writeString(title)
    }

    public companion object {
      public const val MAGIC: UInt = 0x93A5DF73U
    }
  }
}
