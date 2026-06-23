package org.Tajgram.tgnet.model.generated

import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_SavedRingtone : TlGen_Object {
  public data object TL_account_savedRingtone : TlGen_account_SavedRingtone() {
    public const val MAGIC: UInt = 0xB7263F6DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_account_savedRingtoneConverted(
    public val document: TlGen_Document,
  ) : TlGen_account_SavedRingtone() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      document.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1F307EB7U
    }
  }
}
