//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "Buffers.h"
#include <assert.h>
#include <string.h>
#include <exception>
#include <stdexcept>
#include <stdlib.h>
#include "logging.h"

using namespace tgvoip;

#pragma mark - BufferInputStream

BufferInputStream::BufferInputStream(const unsigned char* data, size_t length){
	this->buffer=data;
	this->length=length;
	offset=0;
}

BufferInputStream::BufferInputStream(const Buffer &buffer){
	this->buffer=*buffer;
	this->length=buffer.Length();
	offset=0;
}

BufferInputStream::~BufferInputStream(){

}


void BufferInputStream::Seek(size_t offset){
	if(offset>length){
		throw std::out_of_range("Not enough bytes in buffer");
	}
	this->offset=offset;
}

size_t BufferInputStream::GetLength(){
	return length;
}

size_t BufferInputStream::GetOffset(){
	return offset;
}

size_t BufferInputStream::Remaining(){
	return length-offset;
}

unsigned char BufferInputStream::ReadByte(){
	EnsureEnoughRemaining(1);
	return (unsigned char)buffer[offset++];
}

int32_t BufferInputStream::ReadInt32(){
	EnsureEnoughRemaining(4);
	int32_t res=((int32_t)buffer[offset] & 0xFF) |
			(((int32_t)buffer[offset+1] & 0xFF) << 8) |
			(((int32_t)buffer[offset+2] & 0xFF) << 16) |
			(((int32_t)buffer[offset+3] & 0xFF) << 24);
	offset+=4;
	return res;
}

int64_t BufferInputStream::ReadInt64(){
	EnsureEnoughRemaining(8);
	int64_t res=((int64_t)buffer[offset] & 0xFF) |
			(((int64_t)buffer[offset+1] & 0xFF) << 8) |
			(((int64_t)buffer[offset+2] & 0xFF) << 16) |
			(((int64_t)buffer[offset+3] & 0xFF) << 24) |
			(((int64_t)buffer[offset+4] & 0xFF) << 32) |
			(((int64_t)buffer[offset+5] & 0xFF) << 40) |
			(((int64_t)buffer[offset+6] & 0xFF) << 48) |
			(((int64_t)buffer[offset+7] & 0xFF) << 56);
	offset+=8;
	return res;
}

int16_t BufferInputStream::ReadInt16(){
	EnsureEnoughRemaining(2);
	int16_t res=(uint16_t)buffer[offset] | ((uint16_t)buffer[offset+1] << 8);
	offset+=2;
	return res;
}


int32_t BufferInputStream::ReadTlLength(){
	unsigned char l=ReadByte();
	if(l<254)
		return l;
	assert(length-offset>=3);
	EnsureEnoughRemaining(3);
	int32_t res=((int32_t)buffer[offset] & 0xFF) |
				(((int32_t)buffer[offset+1] & 0xFF) << 8) |
				(((int32_t)buffer[offset+2] & 0xFF) << 16);
	offset+=3;
	return res;
}

void BufferInputStream::ReadBytes(unsigned char *to, size_t count){
	EnsureEnoughRemaining(count);
	memcpy(to, buffer+offset, count);
	offset+=count;
}

void BufferInputStream::ReadBytes(Buffer &to){
	ReadBytes(*to, to.Length());
}

BufferInputStream BufferInputStream::GetPartBuffer(size_t length, bool advance){
	EnsureEnoughRemaining(length);
	BufferInputStream s=BufferInputStream(buffer+offset, length);
	if(advance)
		offset+=length;
	return s;
}

void BufferInputStream::EnsureEnoughRemaining(size_t need){
	if(length-offset<need){
		throw std::out_of_range("Not enough bytes in buffer");
	}
}

#pragma mark - BufferOutputStream

BufferOutputStream::BufferOutputStream(size_t size){
	buffer=(unsigned char*) malloc(size);
	if(!buffer)
		throw std::bad_alloc();
	offset=0;
	this->size=size;
	bufferProvided=false;
}

BufferOutputStream::BufferOutputStream(unsigned char *buffer, size_t size){
	this->buffer=buffer;
	this->size=size;
	offset=0;
	bufferProvided=true;
}

BufferOutputStream::~BufferOutputStream(){
	if(!bufferProvided && buffer)
		free(buffer);
}

void BufferOutputStream::WriteByte(unsigned char byte){
	this->ExpandBufferIfNeeded(1);
	buffer[offset++]=byte;
}

void BufferOutputStream::WriteInt32(int32_t i){
	this->ExpandBufferIfNeeded(4);
	buffer[offset+3]=(unsigned char)((i >> 24) & 0xFF);
	buffer[offset+2]=(unsigned char)((i >> 16) & 0xFF);
	buffer[offset+1]=(unsigned char)((i >> 8) & 0xFF);
	buffer[offset]=(unsigned char)(i & 0xFF);
	offset+=4;
}

void BufferOutputStream::WriteInt64(int64_t i){
	this->ExpandBufferIfNeeded(8);
	buffer[offset+7]=(unsigned char)((i >> 56) & 0xFF);
	buffer[offset+6]=(unsigned char)((i >> 48) & 0xFF);
	buffer[offset+5]=(unsigned char)((i >> 40) & 0xFF);
	buffer[offset+4]=(unsigned char)((i >> 32) & 0xFF);
	buffer[offset+3]=(unsigned char)((i >> 24) & 0xFF);
	buffer[offset+2]=(unsigned char)((i >> 16) & 0xFF);
	buffer[offset+1]=(unsigned char)((i >> 8) & 0xFF);
	buffer[offset]=(unsigned char)(i & 0xFF);
	offset+=8;
}

void BufferOutputStream::WriteInt16(int16_t i){
	this->ExpandBufferIfNeeded(2);
	buffer[offset+1]=(unsigned char)((i >> 8) & 0xFF);
	buffer[offset]=(unsigned char)(i & 0xFF);
	offset+=2;
}

void BufferOutputStream::WriteBytes(const unsigned char *bytes, size_t count){
	this->ExpandBufferIfNeeded(count);
	memcpy(buffer+offset, bytes, count);
	offset+=count;
}

void BufferOutputStream::WriteBytes(const Buffer &buffer){
	WriteBytes(*buffer, buffer.Length());
}

void BufferOutputStream::WriteBytes(const Buffer &buffer, size_t offset, size_t count){
	if(offset+count>buffer.Length())
		throw std::out_of_range("offset out of buffer bounds");
	WriteBytes(*buffer+offset, count);
}

unsigned char *BufferOutputStream::GetBuffer(){
	return buffer;
}

size_t BufferOutputStream::GetLength(){
	return offset;
}

void BufferOutputStream::ExpandBufferIfNeeded(size_t need){
	if(offset+need>size){
		if(bufferProvided){
			throw std::out_of_range("buffer overflow");
		}
		if(need<1024){
			buffer=(unsigned char *) realloc(buffer, size+1024);
			size+=1024;
		}else{
			buffer=(unsigned char *) realloc(buffer, size+need);
			size+=need;
		}
		if(!buffer)
			throw std::bad_alloc();
	}
}


void BufferOutputStream::Reset(){
	offset=0;
}

void BufferOutputStream::Rewind(size_t numBytes){
	if(numBytes>offset)
		throw std::out_of_range("buffer underflow");
	offset-=numBytes;
}

#pragma mark - BufferPool

BufferPool::BufferPool(unsigned int size, unsigned int count){
	assert(count<=64);
	buffers[0]=(unsigned char*) malloc(size*count);
	bufferCount=count;
	unsigned int i;
	for(i=1;i<count;i++){
		buffers[i]=buffers[0]+i*size;
	}
	usedBuffers=0;
	this->size=size;
}

BufferPool::~BufferPool(){
	free(buffers[0]);
}

unsigned char* BufferPool::Get(){
	MutexGuard m(mutex);
	int i;
	for(i=0;i<bufferCount;i++){
		if(!((usedBuffers >> i) & 1)){
			usedBuffers|=(1LL << i);
			return buffers[i];
		}
	}
	return NULL;
}

void BufferPool::Reuse(unsigned char* buffer){
	MutexGuard m(mutex);
	int i;
	for(i=0;i<bufferCount;i++){
		if(buffers[i]==buffer){
			usedBuffers&= ~(1LL << i);
			return;
		}
	}
	LOGE("pointer passed isn't a valid buffer from this pool");
	abort();
}

size_t BufferPool::GetSingleBufferSize(){
	return size;
}

size_t BufferPool::GetBufferCount(){
	return (size_t) bufferCount;
}

#pragma mark - Buffer

