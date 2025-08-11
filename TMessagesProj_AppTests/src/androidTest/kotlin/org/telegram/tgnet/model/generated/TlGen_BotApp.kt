package org.telegram.tgnet.model.generated

import kotlin.Long
import kotlin.String
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_BotApp : TlGen_Object {
  public data object TL_botAppNotModified : TlGen_BotApp() {
    public const val MAGIC: UInt = 0x5DA674B7U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_botApp(
    public val id: Long,
    public val access_hash: Long,
    public val short_name: String,
    public val title: String,
    public val description: String,
    public val photo: TlGen_Photo,
    public val document: TlGen_Document?,
    public val hash: Long,
  ) : TlGen_BotApp() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (document != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeInt64(id)
      stream.writeInt64(access_hash)
      stream.writeString(short_name)
      stream.writeString(title)
      stream.writeString(description)
      photo.serializeToStream(stream)
      document?.serializeToStream(stream)
      stream.writeInt64(hash)
    }

    public companion object {
      public const val MAGIC: UInt = 0x95FCD1D6U
    }
  }
}
