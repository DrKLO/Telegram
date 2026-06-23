package org.Tajgram.tgnet.model.generated

import kotlin.Long
import kotlin.UInt
import org.Tajgram.tgnet.OutputSerializedData
import org.Tajgram.tgnet.model.TlGen_Object
import org.Tajgram.tgnet.model.TlGen_Vector

public sealed class TlGen_ContactBirthday : TlGen_Object {
  public data class TL_contactBirthday(
    public val contact_id: Long,
    public val birthday: TlGen_Birthday,
  ) : TlGen_ContactBirthday() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt64(contact_id)
      birthday.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x1D998733U
    }
  }
}
