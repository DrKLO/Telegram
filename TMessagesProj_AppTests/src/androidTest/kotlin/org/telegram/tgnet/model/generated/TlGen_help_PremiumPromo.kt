package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_help_PremiumPromo : TlGen_Object {
  public data class TL_help_premiumPromo(
    public val status_text: String,
    public val status_entities: List<TlGen_MessageEntity>,
    public val video_sections: List<String>,
    public val videos: List<TlGen_Document>,
    public val period_options: List<TlGen_PremiumSubscriptionOption>,
    public val users: List<TlGen_User>,
  ) : TlGen_help_PremiumPromo() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(status_text)
      TlGen_Vector.serialize(stream, status_entities)
      TlGen_Vector.serializeString(stream, video_sections)
      TlGen_Vector.serialize(stream, videos)
      TlGen_Vector.serialize(stream, period_options)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0x5334759CU
    }
  }
}
