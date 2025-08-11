package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureValueType : TlGen_Object {
  public data object TL_secureValueTypePersonalDetails : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x9D2A81E3U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypePassport : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x3DAC6A00U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeDriverLicense : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x06E425C4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeIdentityCard : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0xA0D0744BU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeInternalPassport : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x99A48F23U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeAddress : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0xCBE31E26U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeUtilityBill : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0xFC36954EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeBankStatement : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x89137C0DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeRentalAgreement : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x8B883488U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypePassportRegistration : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x99E3806AU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeTemporaryRegistration : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0xEA02EC33U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypePhone : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0xB320AADBU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_secureValueTypeEmail : TlGen_SecureValueType() {
    public const val MAGIC: UInt = 0x8E3CA7EEU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
