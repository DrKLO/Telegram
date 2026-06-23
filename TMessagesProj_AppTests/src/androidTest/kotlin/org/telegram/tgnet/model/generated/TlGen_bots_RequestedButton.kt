package org.Tajgram.tgnet.model.generated

import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_bots_RequestedButton : TlGen_Object {
  public data class TL_bots_requestedButton(
    public val webapp_req_id: String,
  ) : TlGen_bots_RequestedButton() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(webapp_req_id)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF13BBCD7U
    }
  }
}
