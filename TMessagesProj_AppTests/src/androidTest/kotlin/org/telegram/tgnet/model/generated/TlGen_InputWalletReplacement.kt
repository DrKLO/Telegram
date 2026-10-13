package org.telegram.tgnet.model.generated

import kotlin.Byte
import kotlin.UInt
import kotlin.collections.List
import org.telegram.tgnet.OutputSerializedData
import org.telegram.tgnet.model.TlGen_Object
import org.telegram.tgnet.model.TlGen_Vector

public sealed class TlGen_InputWalletReplacement : TlGen_Object {
  public data object TL_inputWalletNew : TlGen_InputWalletReplacement() {
    public const val MAGIC: UInt = 0x63A440DCU

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
    }
  }

  public data class TL_inputWalletImported(
    public val public_key: List<Byte>,
    public val anchor_public_key: List<Byte>?,
    public val proof: TlGen_WalletOwnershipProof,
  ) : TlGen_InputWalletReplacement() {
    internal val flags: UInt
      get() {
        var result = 0U
        if (anchor_public_key != null) result = result or 1U
        return result
      }

    public override fun serializeToStream(stream: OutputSerializedData) {
      stream.writeInt32(MAGIC.toInt())
      stream.writeInt32(flags.toInt())
      stream.writeByteArray(public_key.toByteArray())
      anchor_public_key?.let { stream.writeByteArray(it.toByteArray()) }
      proof.serializeToStream(stream)
    }

    public companion object {
      public const val MAGIC: UInt = 0x66A66A3BU
    }
  }
}
