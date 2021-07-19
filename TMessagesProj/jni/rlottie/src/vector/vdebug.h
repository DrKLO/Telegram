/* 
 * Copyright (c) 2018 Samsung Electronics Co., Ltd. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#ifndef VDEBUG_H
#define VDEBUG_H

#include "config.h"

#ifdef LOTTIE_LOGGING_SUPPORT

#include <cstdint>
#include <iosfwd>
#include <memory>
#include <string>
#include <type_traits>

enum class LogLevel : uint8_t { INFO, WARN, CRIT, OFF };

class VDebug {
public:
    VDebug();
    VDebug& debug() { return *this; }
    VDebug(LogLevel level, char const* file, char const* function,
           uint32_t line);
    ~VDebug();

    VDebug(VDebug&&) = default;
    VDebug& operator=(VDebug&&) = default;

    void stringify(std::ostream& os);

    VDebug& operator<<(char arg);
    VDebug& operator<<(int32_t arg);
    VDebug& operator<<(uint32_t arg);
    // VDebug& operator<<(int64_t arg);
    // VDebug& operator<<(uint64_t arg);

    VDebug& operator<<(long arg);
    VDebug& operator<<(unsigned long arg);
    VDebug& operator<<(double arg);
    VDebug& operator<<(std::string const& arg);

    template <size_t N>
    VDebug& operator<<(const char (&arg)[N])
    {
        encode(string_literal_t(arg));
        return *this;
    }

    template <typename Arg>
    typename std::enable_if<std::is_same<Arg, char const*>::value,
                            VDebug&>::type
    operator<<(Arg const& arg)
    {
        encode(arg);
        return *this;
    }

    template <typename Arg>
    typename std::enable_if<std::is_same<Arg, char*>::value, VDebug&>::type
    operator<<(Arg const& arg)
    {
        encode(arg);
        return *this;
    }

    struct string_literal_t {
        explicit string_literal_t(char const* s) : m_s(s) {}
        char const* m_s;
    };

private:
    char* buffer();

    template <typename Arg>
    void encode(Arg arg);

    template <typename Arg>
    void encode(Arg arg, uint8_t type_id);

    void encode(char* arg);
    void encode(char const* arg);
    void encode(string_literal_t arg);
    void encode_c_string(char const* arg, size_t length);
    void resize_buffer_if_needed(size_t additional_bytes);
    void stringify(std::ostream& os, char* start, char const* const end);

private:
    size_t                  m_bytes_used{0};
    size_t                  m_buffer_size{0};
    std::unique_ptr<char[]> m_heap_buffer;
    bool                    m_logAll;
    char m_stack_buffer[256 - sizeof(bool) - 2 * sizeof(size_t) -
                        sizeof(decltype(m_heap_buffer)) - 8 /* Reserved */];
};

struct VDebugServer {
    /*
     * Ideally this should have been operator+=
     * Could not get that to compile, so here we are...
     */
    bool operator==(VDebug&);
};

void set_log_level(LogLevel level);

bool is_logged(LogLevel level);

/*
 * Non guaranteed logging. Uses a ring buffer to hold log lines.
 * When the ring gets full, the previous log line in the slot will be dropped.
 * Does not block producer even if the ring buffer is full.
 * ring_buffer_size_mb - LogLines are pushed into a mpsc ring buffer whose size
 * is determined by this parameter. Since each LogLine is 256 bytes,
 * ring_buffer_size = ring_buffer_size_mb * 1024 * 1024 / 256
 */
struct NonGuaranteedLogger {
    NonGuaranteedLogger(uint32_t ring_buffer_size_mb_)
        : ring_buffer_size_mb(ring_buffer_size_mb_)
    {
    }
    uint32_t ring_buffer_size_mb;
};

/*
 * Provides a guarantee log lines will not be dropped.
 */
struct GuaranteedLogger {
};

/*
 * Ensure initialize() is called prior to any log statements.
 * log_directory - where to create the logs. For example - "/tmp/"
 * log_file_name - root of the file name. For example - "nanolog"
 * This will create log files of the form -
 * /tmp/nanolog.1.txt
 * /tmp/nanolog.2.txt
 * etc.
 * log_file_roll_size_mb - mega bytes after which we roll to next log file.
 */
void initialize(GuaranteedLogger gl, std::string const& log_directory,
                std::string const& log_file_name,
                uint32_t           log_file_roll_size_mb);
void initialize(NonGuaranteedLogger ngl, std::string const& log_directory,
                std::string const& log_file_name,
                uint32_t           log_file_roll_size_mb);

#define VDEBUG_LOG(LEVEL) \
    VDebugServer() == VDebug(LEVEL, __FILE__, __func__, __LINE__).debug()
#define vDebug is_logged(LogLevel::INFO) && VDEBUG_LOG(LogLevel::INFO)
#define vWarning is_logged(LogLevel::WARN) && VDEBUG_LOG(LogLevel::WARN)
#define vCritical is_logged(LogLevel::CRIT) && VDEBUG_LOG(LogLevel::CRIT)

#else

struct VDebug
{
    template<typename Args>
    VDebug& operator<<(const Args &){return *this;}
};

#define vDebug VDebug()
#define vWarning VDebug()
#define vCritical VDebug()

#endif //LOTTIE_LOGGING_SUPPORT

#endif  // VDEBUG_H
