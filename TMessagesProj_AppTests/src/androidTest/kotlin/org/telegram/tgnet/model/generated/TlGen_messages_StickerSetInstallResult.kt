package org.telegram.tgnet.model.generated

import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_StickerSetInstallResult : TlGen_Object {
  public data object TL_messages_stickerSetInstallResultSuccess :
      TlGen_messages_StickerSetInstallResult() {
    public const val MAGIC: UInt = 0x38641628U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_messages_stickerSetInstallResultArchive(
    public val sets: List<TlGen_StickerSetCovered>,
  ) : TlGen_messages_StickerSetInstallResult() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, sets)
    }

    public companion object {
      public const val MAGIC: UInt = 0x35E410A8U
    }
  }
}
