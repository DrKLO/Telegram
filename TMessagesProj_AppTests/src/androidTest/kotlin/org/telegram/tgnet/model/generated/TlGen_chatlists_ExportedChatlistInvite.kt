package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_chatlists_ExportedChatlistInvite : TlGen_Object {
  public data class TL_chatlists_exportedChatlistInvite(
    public val filter: TlGen_DialogFilter,
    public val invite: TlGen_ExportedChatlistInvite,
  ) : TlGen_chatlists_ExportedChatlistInvite() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      filter.serializeToStream(stream)
      invite.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x10E6E3A6U
    }
  }
}
