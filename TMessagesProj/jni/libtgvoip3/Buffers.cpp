//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "Buffers.h"

#include <cassert>
#include <cstdlib>
#include <cstring>
#include <stdexcept>

using namespace tgvoip;

#pragma mark - Buffer

Buffer::Buffer(std::size_t capacity)
    : m_length(capacity)
{
    if (capacity > 0)
    {
        m_data = reinterpret_cast<std::uint8_t*>(std::malloc(capacity));
        if (m_data == nullptr)
            throw std::bad_alloc();
    }
}

Buffer::Buffer(Buffer&& other) noexcept
    : m_data(other.m_data)
    , m_length(other.m_length)
    , m_freeFn(std::move(other.m_freeFn))
    , m_reallocFn(std::move(other.m_reallocFn))
{
    other.m_data = nullptr;
}

Buffer::Buffer(BufferOutputStream&& stream)
    : m_data(stream.m_buffer)
    , m_length(stream.m_offset)
{
    stream.m_buffer = nullptr;
}

Buffer::Buffer() = default;

Buffer::~Buffer()
{
    if (m_data != nullptr)
    {
        if (m_freeFn)
            m_freeFn(m_data);
        else
            std::free(m_data);
    }
    m_data = nullptr;
    m_length = 0;
}

Buffer& Buffer::operator=(Buffer&& other) noexcept
{
    if (this != &other)
    {
        if (m_data != nullptr)
        {
            if (m_freeFn)
                m_freeFn(m_data);
            else
                std::free(m_data);
        }
        m_data = other.m_data;
        m_length = other.m_length;
        m_freeFn = other.m_freeFn;
        m_reallocFn = other.m_reallocFn;
        other.m_data = nullptr;
        other.m_length = 0;
    }
    return *this;
}

std::uint8_t& Buffer::operator[](std::size_t i)
{
    if (i >= m_length)
        throw std::out_of_range("Buffer operator[] invalid index " + std::to_string(i) + ", length is " + std::to_string(m_length));
    return m_data[i];
}

const std::uint8_t& Buffer::operator[](std::size_t i) const
{
    if (i >= m_length)
        throw std::out_of_range("Buffer operator[] invalid index " + std::to_string(i) + ", length is " + std::to_string(m_length));
    return m_data[i];
}

std::uint8_t* Buffer::operator*()
{
    return m_data;
}

const std::uint8_t* Buffer::operator*() const
{
    return m_data;
}

void Buffer::CopyFrom(const Buffer& other, std::size_t count, std::size_t srcOffset, std::size_t dstOffset)
{
    if (other.m_data == nullptr)
        throw std::invalid_argument("Buffer::CopyFrom can't copy from nullptr");
    if (other.m_length < srcOffset + count || m_length < dstOffset + count)
        throw std::out_of_range("Out of offset+count bounds of either buffer");
    std::memcpy(m_data + dstOffset, other.m_data + srcOffset, count);
}

void Buffer::CopyFrom(const void* ptr, std::size_t dstOffset, std::size_t count)
{
    if (m_length < dstOffset + count)
        throw std::out_of_range("Offset+count is out of bounds");
    std::memcpy(m_data + dstOffset, ptr, count);
}

void Buffer::Resize(std::size_t newSize)
{
    std::uint8_t* newData;

    if (m_reallocFn)
        newData = reinterpret_cast<std::uint8_t*>(m_reallocFn(m_data, newSize));
    else
        newData = reinterpret_cast<std::uint8_t*>(std::realloc(m_data, newSize));

    if (newData == nullptr)
    {
        if (m_freeFn)
            m_freeFn(m_data);
        else
            std::free(m_data);

        throw std::bad_alloc();
    }

    m_data = newData;
    m_length = newSize;
}

std::size_t Buffer::Length() const
{
    return m_length;
}

bool Buffer::IsEmpty() const
{
    return (m_length == 0) || (m_data == nullptr);
}

Buffer Buffer::CopyOf(const Buffer& other)
{
    if (other.IsEmpty())
        return Buffer();
    Buffer buf(other.m_length);
    buf.CopyFrom(other, other.m_length);
    return buf;
}

Buffer Buffer::CopyOf(const Buffer& other, std::size_t offset, std::size_t length)
{
    if (offset + length > other.Length())
        throw std::out_of_range("offset+length out of bounds");
    Buffer buf(length);
    buf.CopyFrom(other, length, offset);
    return buf;
}

Buffer Buffer::Wrap(std::uint8_t* data, std::size_t size, std::function<void(void*)> freeFn, std::function<void*(void*, std::size_t)> reallocFn)
{
    Buffer b = Buffer();
    b.m_data = data;
    b.m_length = size;
    b.m_freeFn = std::move(freeFn);
    b.m_reallocFn = std::move(reallocFn);
    return b;
}

#pragma mark - BufferInputStream

BufferInputStream::BufferInputStream(const std::uint8_t* data, std::size_t length)
    : m_buffer(data)
    , m_length(length)
    , m_offset(0)
{
}

BufferInputStream::BufferInputStream(const Buffer& buffer)
    : m_buffer(*buffer)
    , m_length(buffer.Length())
    , m_offset(0)
{
}

void BufferInputStream::Seek(std::size_t offset)
{
    if (offset > m_length)
    {
        throw std::out_of_range("Not enough bytes in buffer");
    }
    m_offset = offset;
}

std::size_t BufferInputStream::GetLength() const
{
    return m_length;
}

std::size_t BufferInputStream::GetOffset() const
{
    return m_offset;
}

std::size_t BufferInputStream::Remaining() const
{
    return m_length - m_offset;
}

std::int8_t BufferInputStream::ReadInt8()
{
    EnsureEnoughRemaining(1);
    return reinterpret_cast<const std::int8_t&>(m_buffer[m_offset++]);
}

std::uint8_t BufferInputStream::ReadUInt8()
{
    EnsureEnoughRemaining(1);
    return m_buffer[m_offset++];
}

std::int16_t BufferInputStream::ReadInt16()
{
    EnsureEnoughRemaining(2);
    std::int16_t res = static_cast<std::int16_t>(((m_buffer[m_offset + 0]) & 0xFF) << 0) |
                       static_cast<std::int16_t>(((m_buffer[m_offset + 1]) & 0xFF) << 8);
    m_offset += 2;
    return res;
}

std::uint16_t BufferInputStream::ReadUInt16()
{
    EnsureEnoughRemaining(2);
    std::uint16_t res = static_cast<std::uint16_t>(((m_buffer[m_offset + 0]) & 0xFF) << 0) |
                        static_cast<std::uint16_t>(((m_buffer[m_offset + 1]) & 0xFF) << 8);
    m_offset += 2;
    return res;
}

std::int32_t BufferInputStream::ReadInt32()
{
    EnsureEnoughRemaining(4);
    std::int32_t res = ((static_cast<std::int32_t>(m_buffer[m_offset + 0]) & 0xFF) <<  0) |
                       ((static_cast<std::int32_t>(m_buffer[m_offset + 1]) & 0xFF) <<  8) |
                       ((static_cast<std::int32_t>(m_buffer[m_offset + 2]) & 0xFF) << 16) |
                       ((static_cast<std::int32_t>(m_buffer[m_offset + 3]) & 0xFF) << 24);
    m_offset += 4;
    return res;
}

std::uint32_t BufferInputStream::ReadUInt32()
{
    EnsureEnoughRemaining(4);
    std::uint32_t res = ((static_cast<std::uint32_t>(m_buffer[m_offset + 0]) & 0xFF) <<  0) |
                        ((static_cast<std::uint32_t>(m_buffer[m_offset + 1]) & 0xFF) <<  8) |
                        ((static_cast<std::uint32_t>(m_buffer[m_offset + 2]) & 0xFF) << 16) |
                        ((static_cast<std::uint32_t>(m_buffer[m_offset + 3]) & 0xFF) << 24);
    m_offset += 4;
    return res;
}

std::int64_t BufferInputStream::ReadInt64()
{
    EnsureEnoughRemaining(8);
    std::int64_t res = ((static_cast<std::int64_t>(m_buffer[m_offset + 0]) & 0xFF) <<  0) |
                       ((static_cast<std::int64_t>(m_buffer[m_offset + 1]) & 0xFF) <<  8) |
                       ((static_cast<std::int64_t>(m_buffer[m_offset + 2]) & 0xFF) << 16) |
                       ((static_cast<std::int64_t>(m_buffer[m_offset + 3]) & 0xFF) << 24) |
                       ((static_cast<std::int64_t>(m_buffer[m_offset + 4]) & 0xFF) << 32) |
                       ((static_cast<std::int64_t>(m_buffer[m_offset + 5]) & 0xFF) << 40) |
                       ((static_cast<std::int64_t>(m_buffer[m_offset + 6]) & 0xFF) << 48) |
                       ((static_cast<std::int64_t>(m_buffer[m_offset + 7]) & 0xFF) << 56);
    m_offset += 8;
    return res;
}

std::uint64_t BufferInputStream::ReadUInt64()
{
    EnsureEnoughRemaining(8);
    std::uint64_t res = ((static_cast<std::uint64_t>(m_buffer[m_offset + 0]) & 0xFF) <<  0) |
                        ((static_cast<std::uint64_t>(m_buffer[m_offset + 1]) & 0xFF) <<  8) |
                        ((static_cast<std::uint64_t>(m_buffer[m_offset + 2]) & 0xFF) << 16) |
                        ((static_cast<std::uint64_t>(m_buffer[m_offset + 3]) & 0xFF) << 24) |
                        ((static_cast<std::uint64_t>(m_buffer[m_offset + 4]) & 0xFF) << 32) |
                        ((static_cast<std::uint64_t>(m_buffer[m_offset + 5]) & 0xFF) << 40) |
                        ((static_cast<std::uint64_t>(m_buffer[m_offset + 6]) & 0xFF) << 48) |
                        ((static_cast<std::uint64_t>(m_buffer[m_offset + 7]) & 0xFF) << 56);
    m_offset += 8;
    return res;
}

std::int32_t BufferInputStream::ReadTlLength()
{
    std::uint8_t l = ReadUInt8();
    if (l < 254)
        return l;
    assert(m_length - m_offset >= 3);
    EnsureEnoughRemaining(3);
    std::int32_t res = ((static_cast<std::int32_t>(m_buffer[m_offset + 0]) & 0xFF) <<  0) |
                       ((static_cast<std::int32_t>(m_buffer[m_offset + 1]) & 0xFF) <<  8) |
                       ((static_cast<std::int32_t>(m_buffer[m_offset + 2]) & 0xFF) << 16);
    m_offset += 3;
    return res;
}

void BufferInputStream::ReadBytes(std::uint8_t* to, std::size_t count)
{
    EnsureEnoughRemaining(count);
    std::memcpy(to, m_buffer + m_offset, count);
    m_offset += count;
}

void BufferInputStream::ReadBytes(Buffer& to)
{
    ReadBytes(*to, to.Length());
}

BufferInputStream BufferInputStream::GetPartBuffer(std::size_t length, bool advance)
{
    EnsureEnoughRemaining(length);
    BufferInputStream s = BufferInputStream(m_buffer + m_offset, length);
    if (advance)
        m_offset += length;
    return s;
}

void BufferInputStream::EnsureEnoughRemaining(std::size_t need)
{
    if (m_length - m_offset < need)
    {
        throw std::out_of_range("Not enough bytes in buffer");
    }
}

#pragma mark - BufferOutputStream

BufferOutputStream::BufferOutputStream(std::size_t size)
    : m_buffer(reinterpret_cast<std::uint8_t*>(std::malloc(size)))
    , m_size(size)
    , m_offset(0)
    , m_bufferProvided(false)
{
    if (m_buffer == nullptr)
        throw std::bad_alloc();
}

BufferOutputStream::BufferOutputStream(std::uint8_t* buffer, std::size_t size)
    : m_buffer(buffer)
    , m_size(size)
    , m_offset(0)
    , m_bufferProvided(true)
{
}

BufferOutputStream& BufferOutputStream::operator=(BufferOutputStream&& other) noexcept
{
    if (this != &other)
    {
        if (!m_bufferProvided && m_buffer != nullptr)
            std::free(m_buffer);
        m_buffer = other.m_buffer;
        m_offset = other.m_offset;
        m_size = other.m_size;
        m_bufferProvided = other.m_bufferProvided;
        other.m_buffer = nullptr;
    }
    return *this;
}

BufferOutputStream::~BufferOutputStream()
{
    if (!m_bufferProvided && m_buffer != nullptr)
        std::free(m_buffer);
}

void BufferOutputStream::WriteUInt8(std::uint8_t byte)
{
    this->ExpandBufferIfNeeded(1);
    m_buffer[m_offset++] = byte;
}

void BufferOutputStream::WriteInt8(std::int8_t byte)
{
    this->ExpandBufferIfNeeded(1);
    m_buffer[m_offset++] = reinterpret_cast<std::uint8_t&>(byte);
}

void BufferOutputStream::WriteInt16(std::int16_t i)
{
    this->ExpandBufferIfNeeded(2);
    m_buffer[m_offset + 1] = static_cast<std::uint8_t>((i >> 8) & 0xFF);
    m_buffer[m_offset + 0] = static_cast<std::uint8_t>((i >> 0) & 0xFF);
    m_offset += 2;
}

void BufferOutputStream::WriteUInt16(std::uint16_t i)
{
    this->ExpandBufferIfNeeded(2);
    m_buffer[m_offset + 1] = static_cast<std::uint8_t>((i >> 8) & 0xFF);
    m_buffer[m_offset + 0] = static_cast<std::uint8_t>((i >> 0) & 0xFF);
    m_offset += 2;
}

void BufferOutputStream::WriteInt32(std::int32_t i)
{
    this->ExpandBufferIfNeeded(4);
    m_buffer[m_offset + 3] = static_cast<std::uint8_t>((i >> 24) & 0xFF);
    m_buffer[m_offset + 2] = static_cast<std::uint8_t>((i >> 16) & 0xFF);
    m_buffer[m_offset + 1] = static_cast<std::uint8_t>((i >>  8) & 0xFF);
    m_buffer[m_offset + 0] = static_cast<std::uint8_t>((i >>  0) & 0xFF);
    m_offset += 4;
}

void BufferOutputStream::WriteUInt32(std::uint32_t i)
{
    this->ExpandBufferIfNeeded(4);
    m_buffer[m_offset + 3] = static_cast<std::uint8_t>((i >> 24) & 0xFF);
    m_buffer[m_offset + 2] = static_cast<std::uint8_t>((i >> 16) & 0xFF);
    m_buffer[m_offset + 1] = static_cast<std::uint8_t>((i >>  8) & 0xFF);
    m_buffer[m_offset + 0] = static_cast<std::uint8_t>((i >>  0) & 0xFF);
    m_offset += 4;
}

void BufferOutputStream::WriteInt64(std::int64_t i)
{
    this->ExpandBufferIfNeeded(8);
    m_buffer[m_offset + 7] = static_cast<std::uint8_t>((i >> 56) & 0xFF);
    m_buffer[m_offset + 6] = static_cast<std::uint8_t>((i >> 48) & 0xFF);
    m_buffer[m_offset + 5] = static_cast<std::uint8_t>((i >> 40) & 0xFF);
    m_buffer[m_offset + 4] = static_cast<std::uint8_t>((i >> 32) & 0xFF);
    m_buffer[m_offset + 3] = static_cast<std::uint8_t>((i >> 24) & 0xFF);
    m_buffer[m_offset + 2] = static_cast<std::uint8_t>((i >> 16) & 0xFF);
    m_buffer[m_offset + 1] = static_cast<std::uint8_t>((i >>  8) & 0xFF);
    m_buffer[m_offset + 0] = static_cast<std::uint8_t>((i >>  0) & 0xFF);
    m_offset += 8;
}

void BufferOutputStream::WriteUInt64(std::uint64_t i)
{
    this->ExpandBufferIfNeeded(8);
    m_buffer[m_offset + 7] = static_cast<std::uint8_t>((i >> 56) & 0xFF);
    m_buffer[m_offset + 6] = static_cast<std::uint8_t>((i >> 48) & 0xFF);
    m_buffer[m_offset + 5] = static_cast<std::uint8_t>((i >> 40) & 0xFF);
    m_buffer[m_offset + 4] = static_cast<std::uint8_t>((i >> 32) & 0xFF);
    m_buffer[m_offset + 3] = static_cast<std::uint8_t>((i >> 24) & 0xFF);
    m_buffer[m_offset + 2] = static_cast<std::uint8_t>((i >> 16) & 0xFF);
    m_buffer[m_offset + 1] = static_cast<std::uint8_t>((i >>  8) & 0xFF);
    m_buffer[m_offset + 0] = static_cast<std::uint8_t>((i >>  0) & 0xFF);
    m_offset += 8;
}

void BufferOutputStream::WriteBytes(const std::uint8_t* bytes, std::size_t count)
{
    this->ExpandBufferIfNeeded(count);
    std::memcpy(m_buffer + m_offset, bytes, count);
    m_offset += count;
}

void BufferOutputStream::WriteBytes(const Buffer& buffer)
{
    WriteBytes(*buffer, buffer.Length());
}

void BufferOutputStream::WriteBytes(const Buffer& buffer, std::size_t offset, std::size_t count)
{
    if (offset + count > buffer.Length())
        throw std::out_of_range("offset out of buffer bounds");
    WriteBytes(*buffer + offset, count);
}

std::uint8_t* BufferOutputStream::GetBuffer() const
{
    return m_buffer;
}

std::size_t BufferOutputStream::GetLength() const
{
    return m_offset;
}

void BufferOutputStream::ExpandBufferIfNeeded(std::size_t need)
{
    if (m_offset + need > m_size)
    {
        if (m_bufferProvided)
        {
            throw std::out_of_range("buffer overflow");
        }
        std::uint8_t* new_buffer;
        need = std::max(need, std::size_t{1024});
        new_buffer = reinterpret_cast<std::uint8_t*>(std::realloc(m_buffer, m_size + need));
        if (new_buffer == nullptr)
        {
            std::free(m_buffer);
            m_buffer = nullptr;
            throw std::bad_alloc();
        }
        m_buffer = new_buffer;
        m_size += need;
    }
}

void BufferOutputStream::Reset()
{
    m_offset = 0;
}

void BufferOutputStream::Rewind(std::size_t numBytes)
{
    if (numBytes > m_offset)
        throw std::out_of_range("buffer underflow");
    m_offset -= numBytes;
}
