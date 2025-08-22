package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_SavedRingtones : TlGen_Object {
  public data object TL_account_savedRingtonesNotModified : TlGen_account_SavedRingtones() {
    public const val MAGIC: UInt = 0xFBF6E8B1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_account_savedRingtones(
    public val hash: Long,
    public val ringtones: List<TlGen_Document>,
  ) : TlGen_account_SavedRingtones() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, ringtones)
    }

    public companion object {
      public const val MAGIC: UInt = 0xC1E92CC5U
    }
  }
}
