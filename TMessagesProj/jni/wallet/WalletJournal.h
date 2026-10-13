#pragma once

#include "wallet_engine.hpp"
#include <cerrno>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

// Each slot is replaced atomically after fsync. flock also serializes separate
// clients/processes, so they cannot both sign against the same journal version.
class WalletJournal {
    class File {
    public:
        int fd;
        explicit File(int value) : fd(value) { if (fd < 0) fail(); }
        ~File() { ::close(fd); }
        File(const File &) = delete;
        File &operator=(const File &) = delete;
    };
    std::string directory_;

    [[noreturn]] static void fail() { throw std::runtime_error("Wallet journal storage failed"); }
    static std::string hex(const std::string &value) {
        std::string result;
        for (unsigned char c : value) {
            result += "0123456789abcdef"[c >> 4];
            result += "0123456789abcdef"[c & 15];
        }
        return result;
    }
    static void sync(int fd) {
        int result;
        do { result = fsync(fd); } while (result < 0 && errno == EINTR);
        if (result < 0) fail();
    }
    static void mkdir_durable(const std::string &path) {
        if (mkdir(path.c_str(), 0700) < 0 && errno != EEXIST) fail();
        // Also sync when recovering a directory created just before a crash.
        File parent(open(path.substr(0, path.rfind('/')).c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC));
        sync(parent.fd);
    }
    static void transfer(int fd, uint8_t *bytes, size_t size, bool writing) {
        while (size) {
            ssize_t n = writing ? write(fd, bytes, size) : read(fd, bytes, size);
            if (n < 0 && errno == EINTR) continue;
            if (n <= 0) fail();
            bytes += n;
            size -= static_cast<size_t>(n);
        }
    }
    static std::optional<wallet_engine::JournalRecord> load(const std::string &path) {
        int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        if (fd < 0 && errno == ENOENT) return std::nullopt;
        File file(fd);
        struct stat info{};
        if (fstat(fd, &info) < 0 || !S_ISREG(info.st_mode) || info.st_size < 8 || info.st_size > 16 * 1024 * 1024) fail();
        uint8_t header[8];
        transfer(fd, header, sizeof(header), false);
        uint64_t version = 0;
        for (uint8_t byte : header) version = (version << 8) | byte;
        if (!version) fail();
        std::vector<uint8_t> payload(static_cast<size_t>(info.st_size) - 8);
        transfer(fd, payload.data(), payload.size(), false);
        return wallet_engine::JournalRecord{version, std::move(payload)};
    }
public:
    explicit WalletJournal(std::string directory) : directory_(std::move(directory)) {}

    wallet_engine::JournalCompareExchangeResult access(
            const wallet_engine::JournalKey &key,
            const wallet_engine::JournalCompareExchange *mutation = nullptr) const {
        mkdir_durable(directory_);
        const auto wallet_dir = directory_ + "/" + hex(key.record_id);
        mkdir_durable(wallet_dir);
        const auto path = wallet_dir + "/" + hex(key.slot);
        File lock(open((path + ".lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600));
        int result;
        do { result = flock(lock.fd, LOCK_EX); } while (result < 0 && errno == EINTR);
        if (result < 0) fail();
        auto current = load(path);
        if (!mutation) return {false, current};
        auto version = current ? std::optional<uint64_t>(current->version) : std::nullopt;
        if (version != mutation->expected_version) return {false, current};
        if (!mutation->replacement.version || (version && mutation->replacement.version <= *version)
                || mutation->replacement.payload.size() > 16 * 1024 * 1024 - 8) fail();
        const auto temp = path + ".tmp";
        File file(open(temp.c_str(), O_CREAT | O_WRONLY | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600));
        uint8_t header[8];
        uint64_t replacement_version = mutation->replacement.version;
        for (int i = 7; i >= 0; --i) {
            header[i] = static_cast<uint8_t>(replacement_version);
            replacement_version >>= 8;
        }
        transfer(file.fd, header, sizeof(header), true);
        const auto &payload = mutation->replacement.payload;
        transfer(file.fd, const_cast<uint8_t *>(payload.data()), payload.size(), true);
        sync(file.fd);
        if (rename(temp.c_str(), path.c_str()) < 0) fail();
        File directory(open(wallet_dir.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC));
        sync(directory.fd);
        return {true, mutation->replacement};
    }
};
