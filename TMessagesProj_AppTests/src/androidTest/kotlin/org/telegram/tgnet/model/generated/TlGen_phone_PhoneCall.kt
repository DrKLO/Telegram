package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_phone_PhoneCall : TlGen_Object {
  public data class TL_phone_phoneCall(
    public val phone_call: TlGen_PhoneCall,
    public val users: List<TlGen_User>,
  ) : TlGen_phone_PhoneCall() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      phone_call.serializeToStream(stream)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEC82E140U
    }
  }
}
