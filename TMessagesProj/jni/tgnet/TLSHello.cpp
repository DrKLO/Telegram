#include "TLSHello.h"
#include "openssl/bn.h"
#include <cassert>

static BIGNUM *get_y2(BIGNUM *x, const BIGNUM *mod, BN_CTX *big_num_context) {
    // returns y^2 = x^3 + 486662 * x^2 + x
    BIGNUM *y = BN_dup(x);
    assert(y != NULL);
    BIGNUM *coef = BN_new();
    BN_set_word(coef, 486662);
    BN_mod_add(y, y, coef, mod, big_num_context);
    BN_mod_mul(y, y, x, mod, big_num_context);
    BN_one(coef);
    BN_mod_add(y, y, coef, mod, big_num_context);
    BN_mod_mul(y, y, x, mod, big_num_context);
    BN_clear_free(coef);
    return y;
}

static BIGNUM *get_double_x(BIGNUM *x, const BIGNUM *mod, BN_CTX *big_num_context) {
    // returns x_2 =(x^2 - 1)^2/(4*y^2)
    BIGNUM *denominator = get_y2(x, mod, big_num_context);
    assert(denominator != NULL);
    BIGNUM *coef = BN_new();
    BN_set_word(coef, 4);
    BN_mod_mul(denominator, denominator, coef, mod, big_num_context);

    BIGNUM *numerator = BN_new();
    assert(numerator != NULL);
    BN_mod_mul(numerator, x, x, mod, big_num_context);
    BN_one(coef);
    BN_mod_sub(numerator, numerator, coef, mod, big_num_context);
    BN_mod_mul(numerator, numerator, numerator, mod, big_num_context);

    BN_mod_inverse(denominator, denominator, mod, big_num_context);
    BN_mod_mul(numerator, numerator, denominator, mod, big_num_context);

    BN_clear_free(coef);
    BN_clear_free(denominator);
    return numerator;
}

static void generate_key_ml_kem_768(unsigned char *key) {
    constexpr uint32_t Q = 3329;
    constexpr int N = 384;

    std::vector<uint32_t> values(N * 2);
    RAND_bytes(reinterpret_cast<unsigned char*>(values.data()),values.size() * sizeof(uint32_t));

    for (int i = 0; i < N; ++i) {
        uint32_t a = values[i * 2]     % Q;
        uint32_t b = values[i * 2 + 1] % Q;

        key[i * 3 + 0] = static_cast<unsigned char>(a & 0xFFu);
        key[i * 3 + 1] = static_cast<unsigned char>((a >> 8) | ((b & 0x0Fu) << 4));
        key[i * 3 + 2] = static_cast<unsigned char>(b >> 4);
    }

    RAND_bytes(key + 1152, 32);
}

static void generate_public_key(unsigned char *key) {
    BIGNUM *mod = NULL;
    BN_hex2bn(&mod, "7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed");
    BIGNUM *pow = NULL;
    BN_hex2bn(&pow, "3ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6");
    BN_CTX *big_num_context = BN_CTX_new();
    assert(big_num_context != NULL);

    BIGNUM *x = BN_new();
    while (1) {
        RAND_bytes(key, 32);
        key[31] &= 127;
        BN_bin2bn(key, 32, x);
        assert(x != NULL);
        BN_mod_mul(x, x, x, mod, big_num_context);

        BIGNUM *y = get_y2(x, mod, big_num_context);

        BIGNUM *r = BN_new();
        BN_mod_exp(r, y, pow, mod, big_num_context);
        BN_clear_free(y);
        if (BN_is_one(r)) {
            BN_clear_free(r);
            break;
        }
        BN_clear_free(r);
    }

    int i;
    for (i = 0; i < 3; i++) {
        BIGNUM *x2 = get_double_x(x, mod, big_num_context);
        BN_clear_free(x);
        x = x2;
    }

    int num_size = BN_num_bytes(x);
    assert(num_size <= 32);
    memset(key, '\0', 32 - num_size);
    BN_bn2bin(x, key + (32 - num_size));
    for (i = 0; i < 16; i++) {
        unsigned char t = key[i];
        key[i] = key[31 - i];
        key[31 - i] = t;
    }

    BN_clear_free(x);
    BN_CTX_free(big_num_context);
    BN_clear_free(pow);
    BN_clear_free(mod);
}

TLSHello::TLSHello() {
    RAND_bytes(grease, MAX_GREASE);
    for (int a = 0; a < MAX_GREASE; a++) {
        grease[a] = (uint8_t) ((grease[a] & 0xf0) + 0x0A);
    }
    for (size_t i = 1; i < MAX_GREASE; i += 2) {
        if (grease[i] == grease[i + 1]) {
            grease[i] ^= 0x10;
        }
    }
}

TLSHello::Op TLSHello::Op::string(const char str[], size_t len) {
    Op res;
    res.type = Type::String;
    res.data = std::string(str, len);
    return res;
}

TLSHello::Op TLSHello::Op::random(size_t length) {
    Op res;
    res.type = Type::Random;
    res.length = length;
    return res;
}

TLSHello::Op TLSHello::Op::K() {
    Op res;
    res.type = Type::K;
    res.length = 32;
    return res;
}

TLSHello::Op TLSHello::Op::E() {
    Op res;
    res.type = Type::E;
    return res;
}

TLSHello::Op TLSHello::Op::M() {
    Op res;
    res.type = Type::M;
    return res;
}

TLSHello::Op TLSHello::Op::P() {
    Op res;
    res.type = Type::P;
    return res;
}

TLSHello::Op TLSHello::Op::zero(size_t length) {
    Op res;
    res.type = Type::Zero;
    res.length = length;
    return res;
}

TLSHello::Op TLSHello::Op::domain() {
    Op res;
    res.type = Type::Domain;
    return res;
}

TLSHello::Op TLSHello::Op::grease(int seed) {
    Op res;
    res.type = Type::Grease;
    res.seed = seed;
    return res;
}

TLSHello::Op TLSHello::Op::begin_scope() {
    Op res;
    res.type = Type::BeginScope;
    return res;
}

TLSHello::Op TLSHello::Op::end_scope() {
    Op res;
    res.type = Type::EndScope;
    return res;
}

TLSHello::Op TLSHello::Op::permutation(std::vector<std::vector<Op>> entities) {
    Op res;
    res.type = Type::Permutation;
    res.entities = std::move(entities);
    return res;
}

const TLSHello &TLSHello::getDefault() {
    static TLSHello result = [] {
        TLSHello res;
        res.ops = {
                Op::string("\x16\x03\x01", 3),
                Op::begin_scope(),
                Op::string("\x01\x00", 2),
                Op::begin_scope(),
                Op::string("\x03\x03", 2),
                Op::zero(32),
                Op::string("\x20", 1),
                Op::random(32),
                Op::string("\x00\x20", 2),
                Op::grease(0),
                Op::string("\x13\x01\x13\x02\x13\x03\xc0\x2b\xc0\x2f\xc0\x2c\xc0\x30\xcc\xa9\xcc\xa8\xc0\x13\xc0\x14\x00\x9c\x00\x9d\x00\x2f\x00\x35\x01\x00", 32),
                Op::begin_scope(),
                Op::grease(2),
                Op::string("\x00\x00", 2),
                Op::permutation({
                                        {
                                                Op::string("\x00\x00", 2),
                                                Op::begin_scope(),
                                                Op::begin_scope(),
                                                Op::string("\x00", 1),
                                                Op::begin_scope(),
                                                Op::domain(),
                                                Op::end_scope(),
                                                Op::end_scope(),
                                                Op::end_scope()
                                        },
                                        { Op::string("\x00\x05\x00\x05\x01\x00\x00\x00\x00",9) },
                                        {
                                                Op::string("\x00\x0a\x00\x0c\x00\x0a", 6),
                                                Op::grease(4),
                                                Op::string("\x11\xec\x00\x1d\x00\x17\x00\x18", 8)
                                        },
                                        { Op::string("\x00\x0b\x00\x02\x01\x00", 6) },
                                        { Op::string("\x00\x0d\x00\x18\x00\x16\x09\x04\x09\x05\x09\x06\x04\x03\x08\x04\x04\x01\x05\x03\x08\x05\x05\x01\x08\x06\x06\x01",28) },
                                        { Op::string("\x00\x10\x00\x0e\x00\x0c\x02\x68\x32\x08\x68\x74\x74\x70\x2f\x31\x2e\x31", 18) },
                                        { Op::string("\x00\x12\x00\x00", 4) },
                                        { Op::string("\x00\x17\x00\x00", 4) },
                                        { Op::string("\x00\x1b\x00\x03\x02\x00\x02", 7) },
                                        { Op::string("\x00\x23\x00\x00", 4) },
                                        {
                                                Op::string("\x00\x2b\x00\x07\x06", 5),
                                                Op::grease(6),
                                                Op::string("\x03\x04\x03\x03", 4)
                                        },
                                        { Op::string("\x00\x2d\x00\x02\x01\x01", 6) },
                                        {
                                                Op::string("\x00\x33\x04\xef\x04\xed", 6),
                                                Op::grease(4),
                                                Op::string("\x00\x01\x00\x11\xec\x04\xc0", 7),
                                                Op::M(),
                                                Op::K(),
                                                Op::string("\x00\x1d\x00\x20", 4),
                                                Op::K(),
                                        },
                                        { Op::string("\x44\xcd\x00\x05\x00\x03\x02\x68\x32", 9) },
                                        {
                                                Op::string("\xfe\x0d", 2),
                                                Op::begin_scope(),
                                                Op::string("\x00\x00\x01\x00\x01", 5),
                                                Op::random(1),
                                                Op::string("\x00\x20", 2),
                                                Op::K(),
                                                Op::begin_scope(),
                                                Op::E(),
                                                Op::end_scope(),
                                                Op::end_scope()
                                        },
                                        { Op::string("\xff\x01\x00\x01\x00", 5) }
                                }),
                Op::grease(3),
                Op::string("\x00\x01\x00", 3),
                Op::P(),
                Op::end_scope(),
                Op::end_scope(),
                Op::end_scope()
        };
        return res;
    }();
    return result;
}

uint32_t TLSHello::writeToBuffer(uint8_t *data) {
    uint32_t offset = 0;
    for (auto op : ops) {
        writeOp(op, data, offset);
    }
    return offset;
}

void TLSHello::setDomain(std::string value) {
    domain = std::move(value);
}

void TLSHello::writeOp(const TLSHello::Op &op, uint8_t *data, uint32_t &offset) {
    using Type = TLSHello::Op::Type;
    switch (op.type) {
        case Type::String:
            memcpy(data + offset, op.data.data(), op.data.size());
            offset += op.data.size();
            break;
        case Type::Random:
            RAND_bytes(data + offset, (size_t) op.length);
            offset += op.length;
            break;
        case Type::K:
            generate_public_key(data + offset);
            offset += op.length;
            break;
        case Type::M:
            generate_key_ml_kem_768(data + offset);
            offset += 1184;
            break;
        case Type::Zero:
            std::memset(data + offset, 0, op.length);
            offset += op.length;
            break;
        case Type::Domain: {
            size_t size = domain.size();
            if (size > 253) {
                size = 253;
            }
            memcpy(data + offset, domain.data(), size);
            offset += size;
            break;
        }
        case Type::Grease: {
            data[offset] = grease[op.seed];
            data[offset + 1] = grease[op.seed];
            offset += 2;
            break;
        }
        case Type::BeginScope:
            scopeOffset.push_back(offset);
            offset += 2;
            break;
        case Type::EndScope: {
            auto begin_offset = scopeOffset.back();
            scopeOffset.pop_back();
            size_t size = offset - begin_offset - 2;
            data[begin_offset] = static_cast<uint8_t>((size >> 8) & 0xff);
            data[begin_offset + 1] = static_cast<uint8_t>(size & 0xff);
            break;
        }
        case Type::E: {
            size_t r = rand() % 4;
            size_t length = (r == 0 ? 144 :
                             (r == 1 ? 176 :
                              (r == 2 ? 208 : 240)));
            RAND_bytes(data + offset, (size_t) length);
            offset += length;
            break;
        }
        case Type::P: {
            auto length = offset;
            if (length <= 513) {
                writeOp(Op::string("\x00\x15", 2), data, offset);
                writeOp(Op::begin_scope(), data, offset);
                writeOp(Op::zero(513 - length), data, offset);
                writeOp(Op::end_scope(), data, offset);
            }
            break;
        }
        case Type::Permutation: {
            std::vector<std::vector<Op>> list = {};
            for (const auto &part : op.entities) {
                list.push_back(part);
            }
            size_t size = list.size();
            for (int i = 0; i < size - 1; i++) {
                int j = i + rand() % (size - i);
                if (i != j) {
                    std::swap(list[i], list[j]);
                }
            }
            for (const auto &part : list) {
                for (const auto &op_local: part) {
                    writeOp(op_local, data, offset);
                }
            }
            break;
        }
    }
}