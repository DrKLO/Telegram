#ifndef TLSHELLO_H
#define TLSHELLO_H

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <openssl/rand.h>

// NOTE: MAX_GREASE and the key-generation helpers below were not part of
// TLSHello.cpp as uploaded — they must already exist somewhere in the
// project (the original file relied on them via #include "TLSHello.h").
// Adjust/remove these declarations to match the real ones.
#ifndef MAX_GREASE
#define MAX_GREASE 8
#endif

class TLSHello {
public:
    TLSHello();

    struct Op {
        enum class Type {
            String, Random, K, M, P, E, Zero, Domain, Grease, BeginScope, EndScope, Permutation
        };
        Type type;
        size_t length = 0;
        int seed = 0;
        std::string data;
        std::vector<std::vector<Op>> entities;

        static Op string(const char str[], size_t len);
        static Op random(size_t length);
        static Op K();
        static Op E();
        static Op M();
        static Op P();
        static Op zero(size_t length);
        static Op domain();
        static Op grease(int seed);
        static Op begin_scope();
        static Op end_scope();
        static Op permutation(std::vector<std::vector<Op>> entities);
    };

    static const TLSHello &getDefault();

    uint32_t writeToBuffer(uint8_t *data);

    void setDomain(std::string value);

private:
    std::vector<Op> ops;
    uint8_t grease[MAX_GREASE];
    std::vector<size_t> scopeOffset;
    std::string domain;

    void writeOp(const Op &op, uint8_t *data, uint32_t &offset);
};

#endif // TLSHELLO_H