//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_BLOCKINGQUEUE_H
#define LIBTGVOIP_BLOCKINGQUEUE_H

#include <stdlib.h>
#include <list>
#include "threading.h"
#include "utils.h"

namespace tgvoip{

template<typename T>
class BlockingQueue{
public:
	TGVOIP_DISALLOW_COPY_AND_ASSIGN(BlockingQueue);
	BlockingQueue(size_t capacity) : semaphore(capacity, 0){
		this->capacity=capacity;
		overflowCallback=NULL;
	};

	~BlockingQueue(){
		semaphore.Release();
	}

	void Put(T thing){
		MutexGuard sync(mutex);
		queue.push_back(std::move(thing));
		bool didOverflow=false;
		while(queue.size()>capacity){
			didOverflow=true;
			if(overflowCallback){
				overflowCallback(std::move(queue.front()));
				queue.pop_front();
			}else{
				abort();
			}
		}
		if(!didOverflow)
			semaphore.Release();
	}

	T GetBlocking(){
		semaphore.Acquire();
		MutexGuard sync(mutex);
		return GetInternal();
	}

	T Get(){
		MutexGuard sync(mutex);
		if(queue.size()>0)
			semaphore.Acquire();
		return GetInternal();
	}

	unsigned int Size(){
		return queue.size();
	}

	void PrepareDealloc(){

	}

	void SetOverflowCallback(void (*overflowCallback)(T)){
		this->overflowCallback=overflowCallback;
	}

private:
	T GetInternal(){
		//if(queue.size()==0)
		//	return NULL;
		T r=std::move(queue.front());
		queue.pop_front();
		return r;
	}

	std::list<T> queue;
	size_t capacity;
	//tgvoip_lock_t lock;
	Semaphore semaphore;
	Mutex mutex;
	void (*overflowCallback)(T);
};
}

#endif //LIBTGVOIP_BLOCKINGQUEUE_H
