package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_messages_DhConfig : TlGen_Object {
  public data class TL_messages_dhConfigNotModified(
    public val random: List<Byte>,
  ) : TlGen_messages_DhConfig() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeByteArray(random.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0xC0E24635U
    }
  }

  public data class TL_messages_dhConfig(
    public val g: Int,
    public val p: List<Byte>,
    public val version: Int,
    public val random: List<Byte>,
  ) : TlGen_messages_DhConfig() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(g)
      stream.writeByteArray(p.toByteArray())
      stream.writeInt32(version)
      stream.writeByteArray(random.toByteArray())
    }

    public companion object {
      public const val MAGIC: UInt = 0x2C221EDDU
    }
  }
}
