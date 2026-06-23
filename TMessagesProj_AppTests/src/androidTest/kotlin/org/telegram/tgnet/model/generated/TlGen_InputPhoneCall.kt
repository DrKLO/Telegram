package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputPhoneCall : TlGen_Object {
  public data class TL_inputPhoneCall(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputPhoneCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1E36FDEDU
    }
  }
}
