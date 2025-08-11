package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotPreviewMedia : TlGen_Object {
  public data class TL_botPreviewMedia(
    public val date: Int,
    public val media: TlGen_MessageMedia,
  ) : TlGen_BotPreviewMedia() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(date)
      media.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x23E91BA3U
    }
  }
}
