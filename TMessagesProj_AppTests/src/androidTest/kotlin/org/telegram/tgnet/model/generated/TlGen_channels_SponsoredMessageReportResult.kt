package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_channels_SponsoredMessageReportResult : TlGen_Object {
  public data class TL_channels_sponsoredMessageReportResultChooseOption(
    public val title: String,
    public val options: List<TlGen_SponsoredMessageReportOption>,
  ) : TlGen_channels_SponsoredMessageReportResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      TlGen_Vector.serialize(stream, options)
    }

    public companion object {
      public const val MAGIC: UInt = 0x846F9E42U
    }
  }

  public data object TL_channels_sponsoredMessageReportResultAdsHidden :
      TlGen_channels_SponsoredMessageReportResult() {
    public const val MAGIC: UInt = 0x3E3BCF2FU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_channels_sponsoredMessageReportResultReported :
      TlGen_channels_SponsoredMessageReportResult() {
    public const val MAGIC: UInt = 0xAD798849U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
