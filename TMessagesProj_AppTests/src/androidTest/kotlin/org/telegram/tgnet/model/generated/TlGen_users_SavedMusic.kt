package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_users_SavedMusic : TlGen_Object {
  public data class TL_users_savedMusicNotModified(
    public val count: Int,
  ) : TlGen_users_SavedMusic() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE3878AA4U
    }
  }

  public data class TL_users_savedMusic(
    public val count: Int,
    public val documents: List<TlGen_Document>,
  ) : TlGen_users_SavedMusic() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(count)
      TlGen_Vector.serialize(stream, documents)
    }

    public companion object {
      public const val MAGIC: UInt = 0x34A2F297U
    }
  }
}
