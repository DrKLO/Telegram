package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_Bool : TlGen_Object {
  public data object TL_boolFalse : TlGen_Bool() {
    public const val MAGIC: UInt = 0xBC799737U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_boolTrue : TlGen_Bool() {
    public const val MAGIC: UInt = 0x997275B5U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
