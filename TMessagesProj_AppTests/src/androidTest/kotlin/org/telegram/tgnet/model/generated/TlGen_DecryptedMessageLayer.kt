package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_DecryptedMessageLayer : TlGen_Object {
  public data class TL_decryptedMessageLayer_layer17(
    public val random_bytes: List<Byte>,
    public val layer: Int,
    public val in_seq_no: Int,
    public val out_seq_no: Int,
    public val message: TlGen_DecryptedMessage,
  ) : TlGen_DecryptedMessageLayer() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(random_bytes.toByteArray())
      stream.writeInt32(layer)
      stream.writeInt32(in_seq_no)
      stream.writeInt32(out_seq_no)
      message.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1BE31789U
    }
  }
}
