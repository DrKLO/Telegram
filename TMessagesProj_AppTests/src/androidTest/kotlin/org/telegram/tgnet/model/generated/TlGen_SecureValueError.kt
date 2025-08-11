package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.String
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_SecureValueError : TlGen_Object {
  public data class TL_secureValueErrorData(
    public val type: TlGen_SecureValueType,
    public val data_hash: List<Byte>,
    public val `field`: String,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(data_hash.toByteArray())
      stream.writeString(field)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE8A40BD9U
    }
  }

  public data class TL_secureValueErrorFrontSide(
    public val type: TlGen_SecureValueType,
    public val file_hash: List<Byte>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x00BE3DFAU
    }
  }

  public data class TL_secureValueErrorReverseSide(
    public val type: TlGen_SecureValueType,
    public val file_hash: List<Byte>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x868A2AA5U
    }
  }

  public data class TL_secureValueErrorSelfie(
    public val type: TlGen_SecureValueType,
    public val file_hash: List<Byte>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xE537CED6U
    }
  }

  public data class TL_secureValueErrorFile(
    public val type: TlGen_SecureValueType,
    public val file_hash: List<Byte>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x7A700873U
    }
  }

  public data class TL_secureValueErrorFiles(
    public val type: TlGen_SecureValueType,
    public val file_hash: List<List<Byte>>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      TlGen_Vector.serializeBytes(stream, file_hash)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x666220E9U
    }
  }

  public data class TL_secureValueError(
    public val type: TlGen_SecureValueType,
    public val hash: List<Byte>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(hash.toByteArray())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x869D758FU
    }
  }

  public data class TL_secureValueErrorTranslationFile(
    public val type: TlGen_SecureValueType,
    public val file_hash: List<Byte>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      stream.writeByteArray(file_hash.toByteArray())
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0xA1144770U
    }
  }

  public data class TL_secureValueErrorTranslationFiles(
    public val type: TlGen_SecureValueType,
    public val file_hash: List<List<Byte>>,
    public val text: String,
  ) : TlGen_SecureValueError() {
    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      type.serializeToStream(stream)
      TlGen_Vector.serializeBytes(stream, file_hash)
      stream.writeString(text)
    }

    public companion object {
      public const val MAGIC: UInt = 0x34636DD8U
    }
  }
}
