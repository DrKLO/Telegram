package org.telegram.ui.Wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The normalised hash of a real message, as toncenter has it (`hash_norm`). Run by the developer. */
class WalletMessageHashTest {

    /** A wallet's first transfer on mainnet: a StateInit in place, the body in a reference. */
    private val firstTransfer = "b5ee9c720101060100fa000347880030a0d0b51f84462cbd82ea9dfd9b2e92d1f473999c6b2f5132c8930a5d86fff011b00102030030ff00209821d7498308b9f240df8085f833d0ed1e20ed53d9005200000000007fff7f11ef88930342fdde5a7dd652d371de3b508d5d8e8b6dadd8fe90d6dccde0a53efb01a25350c1f941942f18cc2ad9a6bee19955d702662a386f04f042d1dd2b9d2d4a2ee9d41472891305217cd43bbee099c1ca24a897c0938b7e4b9d41d5d4c934c60b63896e757fff7f116ae5fd4d000000000304016642000c28342d47e1118b2f60baa77f66cba4b47d1ce6671acbd44cb224c29761bffc1cc4b40000000000000000000000000001050000"

    private fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun firstTransfer() {
        assertEquals(
            "bf017e0c9861dd17d69a0856f965645b9da1dea7fbf60bc17e853871f8b58097",
            WalletMessageHash.normalized(bytes(firstTransfer)),
        )
    }

    /** The same message without the StateInit, as from a deployed wallet: the normalised form doesn't take it in. */
    @Test
    fun noStateInit() {
        assertEquals(
            "bf017e0c9861dd17d69a0856f965645b9da1dea7fbf60bc17e853871f8b58097",
            WalletMessageHash.normalized(bytes("b5ee9c72010204010000b2000145880030a0d0b51f84462cbd82ea9dfd9b2e92d1f473999c6b2f5132c8930a5d86fff00c0101a25350c1f941942f18cc2ad9a6bee19955d702662a386f04f042d1dd2b9d2d4a2ee9d41472891305217cd43bbee099c1ca24a897c0938b7e4b9d41d5d4c934c60b63896e757fff7f116ae5fd4d000000000302016642000c28342d47e1118b2f60baa77f66cba4b47d1ce6671acbd44cb224c29761bffc1cc4b40000000000000000000000000001030000")),
        )
    }

    @Test
    fun notAMessage() {
        assertNull(WalletMessageHash.normalized(ByteArray(0)))
        assertNull(WalletMessageHash.normalized(bytes(firstTransfer).copyOf(40)))
        // The StateInit itself: a bag of cells, not a message.
        assertNull(WalletMessageHash.normalized(bytes("b5ee9c7201010301004a0002013401020030ff00209821d7498308b9f240df8085f833d0ed1e20ed53d9005200000000007fff7f11ef88930342fdde5a7dd652d371de3b508d5d8e8b6dadd8fe90d6dccde0a53efb")))
    }
}
