package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputTheme : TlGen_Object {
  public data class TL_inputTheme(
    public val id: Long,
    public val access_hash: Long,
  ) : TlGen_InputTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x3C5693E9U
    }
  }

  public data class TL_inputThemeSlug(
    public val slug: String,
  ) : TlGen_InputTheme() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(slug)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF5890DF1U
    }
  }
}
