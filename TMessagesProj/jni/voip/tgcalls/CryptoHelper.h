#ifndef TGCALLS_CRYPTO_HELPER_H
#define TGCALLS_CRYPTO_HELPER_H

extern "C" {
#include <openssl/sha.h>
#include <openssl/aes.h>
#ifndef OPENSSL_IS_BORINGSSL
#include <openssl/modes.h>
#endif
#include <openssl/rand.h>
#include <openssl/crypto.h>
} // extern "C"

#include <array>

namespace tgcalls {

struct MemorySpan {
	MemorySpan(const void *data, size_t size) :
		data(data),
		size(size) {
	}

	const void *data = nullptr;
	size_t size = 0;
};

struct AesKeyIv {
	std::array<uint8_t, 32> key;
	std::array<uint8_t, 16> iv;
};

constexpr auto kSha256Size = size_t(SHA256_DIGEST_LENGTH);

template <typename ...Parts>
void SHA256Update(SHA256_CTX*, Parts &&...parts);

inline void SHA256Update(SHA256_CTX*) {
}

template <typename First, typename ...Others>
inline void SHA256Update(SHA256_CTX *context, First &&span, Others &&...others) {
	static_assert(
		std::is_same<std::decay_t<First>, MemorySpan>::value,
		"Pass some MemorySpan-s here.");

	SHA256_Update(context, span.data, span.size);
	SHA256Update(context, std::forward<Others>(others)...);
}

template <typename ...Parts>
inline std::array<uint8_t, kSha256Size> ConcatSHA256(Parts &&... parts) {
	static_assert(sizeof...(parts) > 0, "empty list");

	auto result = std::array<uint8_t, kSha256Size>();
	auto context = SHA256_CTX();
	SHA256_Init(&context);
	SHA256Update(&context, std::forward<Parts>(parts)...);
	SHA256_Final(result.data(), &context);
	return result;
}

AesKeyIv PrepareAesKeyIv(const uint8_t *key, const uint8_t *msgKey, int x);
void AesProcessCtr(MemorySpan from, void *to, AesKeyIv &&aesKeyIv);

} // namespace tgcalls

#endif
