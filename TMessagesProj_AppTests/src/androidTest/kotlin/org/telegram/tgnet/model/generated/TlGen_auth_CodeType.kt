package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_auth_CodeType : TlGen_Object {
  public data object TL_auth_codeTypeSms : TlGen_auth_CodeType() {
    public const val MAGIC: UInt = 0x72A3158CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_auth_codeTypeCall : TlGen_auth_CodeType() {
    public const val MAGIC: UInt = 0x741CD3E3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_auth_codeTypeFlashCall : TlGen_auth_CodeType() {
    public const val MAGIC: UInt = 0x226CCEFBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_auth_codeTypeMissedCall : TlGen_auth_CodeType() {
    public const val MAGIC: UInt = 0xD61AD6EEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_auth_codeTypeFragmentSms : TlGen_auth_CodeType() {
    public const val MAGIC: UInt = 0x06ED998CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
