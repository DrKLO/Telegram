package org.telegram.tgnet.model.generated

import kotlin.UInt
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_storage_FileType : TlGen_Object {
  public data object TL_storage_fileUnknown : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0xAA963B05U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_filePartial : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0x40BC6F52U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_fileJpeg : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0x007EFE0EU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_fileGif : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0xCAE1AADFU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_filePng : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0x0A4F63C0U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_filePdf : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0xAE1E508DU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_fileMp3 : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0x528A0677U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_fileMov : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0x4B09EBBCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_fileMp4 : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0xB3CEA0E4U

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data object TL_storage_fileWebp : TlGen_storage_FileType() {
    public const val MAGIC: UInt = 0x1081464CU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }
}
