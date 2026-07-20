package org.telegram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

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
