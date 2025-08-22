package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_HistoryImportParsed : TlGen_Object {
  public data class TL_messages_historyImportParsed(
    public val pm: Boolean,
    public val group: Boolean,
    public val title: String?,
  ) : TlGen_messages_HistoryImportParsed() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (pm) result = result or 1U
        if (group) result = result or 2U
        if (title != null) result = result or 4U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      title?.let { stream.writeString(it) }
    }

    public companion object {
      public const val MAGIC: UInt = 0x5E0FB7B9U
    }
  }
}
