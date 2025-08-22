package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_ReportResult : TlGen_Object {
  public data class TL_reportResultChooseOption(
    public val title: String,
    public val options: List<TlGen_MessageReportOption>,
  ) : TlGen_ReportResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(title)
      TlGen_Vector.serialize(stream, options)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF0E4E0B6U
    }
  }

  public data class TL_reportResultAddComment(
    public val optional: Boolean,
    public val option: List<Byte>,
  ) : TlGen_ReportResult() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (optional) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeByteArray(option.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x6F09AC31U
    }
  }

  public data object TL_reportResultReported : TlGen_ReportResult() {
    public const val MAGIC: UInt = 0x8DB33C4BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
