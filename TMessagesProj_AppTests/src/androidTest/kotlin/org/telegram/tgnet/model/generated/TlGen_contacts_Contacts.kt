package org.telegram.tgnet.model.generated

import kotlin.Int
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_contacts_Contacts : TlGen_Object {
  public data object TL_contacts_contactsNotModified : TlGen_contacts_Contacts() {
    public const val MAGIC: UInt = 0xB74BA9D2U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_contacts_contacts(
    public val contacts: List<TlGen_Contact>,
    public val saved_count: Int,
    public val users: List<TlGen_User>,
  ) : TlGen_contacts_Contacts() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      TlGen_Vector.serialize(stream, contacts)
      stream.writeInt32(saved_count)
      TlGen_Vector.serialize(stream, users)
    }

    public companion object {
      public const val MAGIC: UInt = 0xEAE87E42U
    }
  }
}
