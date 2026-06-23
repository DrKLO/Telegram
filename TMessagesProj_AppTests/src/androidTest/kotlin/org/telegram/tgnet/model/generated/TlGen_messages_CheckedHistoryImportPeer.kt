package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_CheckedHistoryImportPeer : TlGen_Object {
  public data class TL_messages_checkedHistoryImportPeer(
    public val confirm_text: String,
  ) : TlGen_messages_CheckedHistoryImportPeer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(confirm_text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA24DE717U
    }
  }
}
