package org.Tajgram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_ChatOnlines : TlGen_Object {
  public data class TL_chatOnlines(
    public val onlines: Int,
  ) : TlGen_ChatOnlines() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(onlines)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF041E250U
    }
  }
}
