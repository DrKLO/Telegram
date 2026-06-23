package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_phone_GroupCallStreamRtmpUrl : TlGen_Object {
  public data class TL_phone_groupCallStreamRtmpUrl(
    public val url: String,
    public val key: String,
  ) : TlGen_phone_GroupCallStreamRtmpUrl() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(url)
      stream.writeString(key)
    }

    public companion object {
      public const val MAGIC: UInt = 0x2DBF3432U
    }
  }
}
