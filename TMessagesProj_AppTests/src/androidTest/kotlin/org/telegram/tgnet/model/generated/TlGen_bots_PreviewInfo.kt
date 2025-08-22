package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_bots_PreviewInfo : TlGen_Object {
  public data class TL_bots_previewInfo(
    public val media: List<TlGen_BotPreviewMedia>,
    public val lang_codes: List<String>,
  ) : TlGen_bots_PreviewInfo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, media)
      TlGen_Vector.serializeString(stream, lang_codes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x0CA71D64U
    }
  }
}
