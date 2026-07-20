package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_LangPackDifference : TlGen_Object {
  public data class TL_langPackDifference(
    public val lang_code: String,
    public val from_version: Int,
    public val version: Int,
    public val strings: List<TlGen_LangPackString>,
  ) : TlGen_LangPackDifference() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeString(lang_code)
      stream.writeInt32(from_version)
      stream.writeInt32(version)
      TlGen_Vector.serialize(stream, strings)
    }

    public companion object {
      public const val MAGIC: UInt = 0xF385C1F6U
    }
  }
}
