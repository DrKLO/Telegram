package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_MessageViews : TlGen_Object {
  public data class TL_messageViews(
    public val views: Int?,
    public val forwards: Int?,
    public val replies: TlGen_MessageReplies?,
  ) : TlGen_MessageViews() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (views != null) result = result or 1U
        if (forwards != null) result = result or 2U
        if (replies != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      views?.let { stream.writeInt32(it) }
      forwards?.let { stream.writeInt32(it) }
      replies?.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x455B853DU
    }
  }
}
