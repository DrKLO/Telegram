package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_phone_ExportedGroupCallInvite : TlGen_Object {
  public data class TL_phone_exportedGroupCallInvite(
    public val link: String,
  ) : TlGen_phone_ExportedGroupCallInvite() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(link)
    }

    public companion object {
      public const val MAGIC: UInt = 0x204BD158U
    }
  }
}
