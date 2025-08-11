package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_account_Themes : TlGen_Object {
  public data object TL_account_themesNotModified : TlGen_account_Themes() {
    public const val MAGIC: UInt = 0xF41EB622U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_account_themes(
    public val hash: Long,
    public val themes: List<TlGen_Theme>,
  ) : TlGen_account_Themes() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(hash)
      TlGen_Vector.serialize(stream, themes)
    }

    public companion object {
      public const val MAGIC: UInt = 0x9A3D8C6DU
    }
  }
}
