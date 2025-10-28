package org.telegram.tgnet.model.generated

import kotlin.Boolean
import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_contacts_ForeignLink : TlGen_Object {
  public data object TL_contacts_foreignLinkUnknown_layer23 : TlGen_contacts_ForeignLink() {
    public const val MAGIC: UInt = 0x133421F8U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_contacts_foreignLinkRequested_layer23(
    public val has_phone: Boolean,
  ) : TlGen_contacts_ForeignLink() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeBool(has_phone)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA7801F47U
    }
  }

  public data object TL_contacts_foreignLinkMutual_layer23 : TlGen_contacts_ForeignLink() {
    public const val MAGIC: UInt = 0x1BEA8CE1U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
