//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_BUFFERINPUTSTREAM_H
#define LIBTGVOIP_BUFFERINPUTSTREAM_H

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdexcept>
#include <array>
#include <limits>
#include <stddef.h>
#include "threading.h"
#include "utils.h"

namespace tgvoip{
	class Buffer;

	class BufferInputStream{

	public:
		BufferInputStream(const unsigned char* data, size_t length);
		BufferInputStream(const Buffer& buffer);
		~BufferInputStream();
		void Seek(size_t offset);
		size_t GetLength();
		size_t GetOffset();
		size_t Remaining();
		unsigned char ReadByte();
		int64_t ReadInt64();
		int32_t ReadInt32();
		int16_t ReadInt16();
		int32_t ReadTlLength();
		void ReadBytes(unsigned char* to, size_t count);
		void ReadBytes(Buffer& to);
		BufferInputStream GetPartBuffer(size_t length, bool advance);

	private:
		void EnsureEnoughRemaining(size_t need);
		const unsigned char* buffer;
		size_t length;
		size_t offset;
	};

	class BufferOutputStream{
	friend class Buffer;
	public:
		TGVOIP_DISALLOW_COPY_AND_ASSIGN(BufferOutputStream);
		BufferOutputStream(size_t size);
		BufferOutputStream(unsigned char* buffer, size_t size);
		~BufferOutputStream();
		void WriteByte(unsigned char byte);
		void WriteInt64(int64_t i);
		void WriteInt32(int32_t i);
		void WriteInt16(int16_t i);
		void WriteBytes(const unsigned char* bytes, size_t count);
		void WriteBytes(const Buffer& buffer);
		void WriteBytes(const Buffer& buffer, size_t offset, size_t count);
		unsigned char* GetBuffer();
		size_t GetLength();
		void Reset();
		void Rewind(size_t numBytes);
		
		BufferOutputStream& operator=(BufferOutputStream&& other){
			if(this!=&other){
				if(!bufferProvided && buffer)
					free(buffer);
				buffer=other.buffer;
				offset=other.offset;
				size=other.size;
				bufferProvided=other.bufferProvided;
				other.buffer=NULL;
			}
			return *this;
		}

	private:
		void ExpandBufferIfNeeded(size_t need);
		unsigned char* buffer=NULL;
		size_t size;
		size_t offset;
		bool bufferProvided;
	};

	class BufferPool{
	public:
		TGVOIP_DISALLOW_COPY_AND_ASSIGN(BufferPool);
		BufferPool(unsigned int size, unsigned int count);
		~BufferPool();
		unsigned char* Get();
		void Reuse(unsigned char* buffer);
		size_t GetSingleBufferSize();
		size_t GetBufferCount();

	private:
		uint64_t usedBuffers;
		int bufferCount;
		size_t size;
		unsigned char* buffers[64];
		Mutex mutex;
	};

	class Buffer{
	public:
		Buffer(size_t capacity){
			if(capacity>0)
				data=(unsigned char *) malloc(capacity);
			else
				data=NULL;
			length=capacity;
		};
		TGVOIP_DISALLOW_COPY_AND_ASSIGN(Buffer); // use Buffer::CopyOf to copy contents explicitly
		Buffer(Buffer&& other) noexcept {
			data=other.data;
			length=other.length;
			other.data=NULL;
		};
		Buffer(BufferOutputStream&& stream){
			data=stream.buffer;
			length=stream.offset;
			stream.buffer=NULL;
		}
		Buffer(){
			data=NULL;
			length=0;
		}
		~Buffer(){
			if(data)
				free(data);
			data=NULL;
		};
		Buffer& operator=(Buffer&& other){
			if(this!=&other){
				if(data)
					free(data);
				data=other.data;
				length=other.length;
				other.data=NULL;
			}
			return *this;
		}
		unsigned char& operator[](size_t i){
			if(i>=length)
				throw std::out_of_range("");
			return data[i];
		}
		const unsigned char& operator[](size_t i) const{
			if(i>=length)
				throw std::out_of_range("");
			return data[i];
		}
		unsigned char* operator*(){
			return data;
		}
		const unsigned char* operator*() const{
			return data;
		}
		void CopyFrom(const Buffer& other, size_t count, size_t srcOffset=0, size_t dstOffset=0){
			if(!other.data)
				throw std::invalid_argument("CopyFrom can't copy from NULL");
			if(other.length<srcOffset+count || length<dstOffset+count)
				throw std::out_of_range("Out of offset+count bounds of either buffer");
			memcpy(data+dstOffset, other.data+srcOffset, count);
		}
		void CopyFrom(const void* ptr, size_t dstOffset, size_t count){
			if(length<dstOffset+count)
				throw std::out_of_range("Offset+count is out of bounds");
			memcpy(data+dstOffset, ptr, count);
		}
		void Resize(size_t newSize){
			data=(unsigned char *) realloc(data, newSize);
			length=newSize;
		}
		size_t Length() const{
			return length;
		}
		bool IsEmpty() const{
			return length==0;
		}
		static Buffer CopyOf(const Buffer& other){
			Buffer buf(other.length);
			buf.CopyFrom(other, other.length);
			return buf;
		}
	private:
		unsigned char* data;
		size_t length;
	};

	template <typename T, size_t size, typename AVG_T=T> class HistoricBuffer{
	public:
		HistoricBuffer(){
			std::fill(data.begin(), data.end(), (T)0);
		}

		AVG_T Average(){
			AVG_T avg=(AVG_T)0;
			for(T& i:data){
				avg+=i;
			}
			return avg/(AVG_T)size;
		}

		AVG_T Average(size_t firstN){
			AVG_T avg=(AVG_T)0;
			for(size_t i=0;i<firstN;i++){
				avg+=(*this)[i];
			}
			return avg/(AVG_T)firstN;
		}

		AVG_T NonZeroAverage(){
			AVG_T avg=(AVG_T)0;
			int nonZeroCount=0;
			for(T& i:data){
				if(i!=0){
					nonZeroCount++;
					avg+=i;
				}
			}
			if(nonZeroCount==0)
				return (AVG_T)0;
			return avg/(AVG_T)nonZeroCount;
		}

		void Add(T el){
			data[offset]=el;
			offset=(offset+1)%size;
		}

		T Min(){
			T min=std::numeric_limits<T>::max();
			for(T& i:data){
				if(i<min)
					min=i;
			}
			return min;
		}

		T Max(){
			T max=std::numeric_limits<T>::min();
			for(T& i:data){
				if(i>max)
					max=i;
			}
			return max;
		}

		void Reset(){
			std::fill(data.begin(), data.end(), (T)0);
			offset=0;
		}

		T& operator[](size_t i){
			assert(i<size);
			// [0] should return the most recent entry, [1] the one before it, and so on
			ptrdiff_t _i=offset-i-1;
			if(_i<0)
				_i=size+_i;
			return data[_i];
		}

		size_t Size(){
			return size;
		}
	private:
		std::array<T, size> data;
		ptrdiff_t offset=0;
	};
}

#endif //LIBTGVOIP_BUFFERINPUTSTREAM_H
