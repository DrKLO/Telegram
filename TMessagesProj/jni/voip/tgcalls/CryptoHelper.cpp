#include "CryptoHelper.h"

#include <cstring>
#include <limits.h>

namespace tgcalls {

AesKeyIv PrepareAesKeyIv(const uint8_t *key, const uint8_t *msgKey, int x) {
	auto result = AesKeyIv();

	const auto sha256a = ConcatSHA256(
		MemorySpan{ msgKey, 16 },
		MemorySpan{ key + x, 36 });
	const auto sha256b = ConcatSHA256(
		MemorySpan{ key + 40 + x, 36 },
		MemorySpan{ msgKey, 16 });
	const auto aesKey = result.key.data();
	const auto aesIv = result.iv.data();
	memcpy(aesKey, sha256a.data(), 8);
	memcpy(aesKey + 8, sha256b.data() + 8, 16);
	memcpy(aesKey + 8 + 16, sha256a.data() + 24, 8);
	memcpy(aesIv, sha256b.data(), 4);
	memcpy(aesIv + 4, sha256a.data() + 8, 8);
	memcpy(aesIv + 4 + 8, sha256b.data() + 24, 4);

	return result;
}

void AesProcessCtr(MemorySpan from, void *to, AesKeyIv &&aesKeyIv) {
	auto aes = AES_KEY();
	AES_set_encrypt_key(
		reinterpret_cast<const unsigned char*>(aesKeyIv.key.data()),
		aesKeyIv.key.size() * CHAR_BIT,
		&aes);

	unsigned char ecountBuf[16] = { 0 };
	unsigned int offsetInBlock = 0;

#ifdef OPENSSL_IS_BORINGSSL
	AES_ctr128_encrypt(
			reinterpret_cast<const unsigned char*>(from.data),
			reinterpret_cast<unsigned char*>(to),
			from.size,
			&aes,
			reinterpret_cast<unsigned char*>(aesKeyIv.iv.data()),
			ecountBuf,
			&offsetInBlock);
#else
	CRYPTO_ctr128_encrypt(
		reinterpret_cast<const unsigned char*>(from.data),
		reinterpret_cast<unsigned char*>(to),
		from.size,
		&aes,
		reinterpret_cast<unsigned char*>(aesKeyIv.iv.data()),
		ecountBuf,
		&offsetInBlock,
		block128_f(AES_encrypt));
#endif
}

} // namespace tgcalls
