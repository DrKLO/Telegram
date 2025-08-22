package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputDocument : TlGen_Object {
  public data object TL_inputDocumentEmpty : TlGen_InputDocument() {
    public const val MAGIC: UInt = 0x72F0EAAEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputDocument(
    public val id: Long,
    public val access_hash: Long,
    public val file_reference: List<Byte>,
  ) : TlGen_InputDocument() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeByteArray(file_reference.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x1ABFB575U
    }
  }
}
