//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef __THREADING_H
#define __THREADING_H

#include <functional>

#if defined(_POSIX_THREADS) || defined(_POSIX_VERSION) || defined(__unix__) || defined(__unix) || (defined(__APPLE__) && defined(__MACH__))

#include <pthread.h>
#include <semaphore.h>
#include <sched.h>
#include <unistd.h>
#ifdef __APPLE__
#include "os/darwin/DarwinSpecific.h"
#endif

namespace tgvoip{
	class Mutex{
	public:
		Mutex(){
			pthread_mutex_init(&mtx, NULL);
		}

		~Mutex(){
			pthread_mutex_destroy(&mtx);
		}

		void Lock(){
			pthread_mutex_lock(&mtx);
		}

		void Unlock(){
			pthread_mutex_unlock(&mtx);
		}

		pthread_mutex_t* NativeHandle(){
			return &mtx;
		}

	private:
		Mutex(const Mutex& other);
		pthread_mutex_t mtx;
	};

	class Thread{
	public:
		Thread(std::function<void()> entry) : entry(entry){
			name=NULL;
			thread=0;
		}

		virtual ~Thread(){

		}

		void Start(){
			if(pthread_create(&thread, NULL, Thread::ActualEntryPoint, this)==0){
				valid=true;
			}
		}

		void Join(){
			if(valid)
				pthread_join(thread, NULL);
		}

		void SetName(const char* name){
			this->name=name;
		}


		void SetMaxPriority(){
#ifdef __APPLE__
			maxPriority=true;
#endif
		}

		static void Sleep(double seconds){
			usleep((useconds_t)(seconds*1000000.0));
		}

		bool IsCurrent(){
			return pthread_equal(thread, pthread_self())!=0;
		}

	private:
		static void* ActualEntryPoint(void* arg){
			Thread* self=reinterpret_cast<Thread*>(arg);
			if(self->name){
#if !defined(__APPLE__) && !defined(__gnu_hurd__)
				pthread_setname_np(self->thread, self->name);
#elif !defined(__gnu_hurd__)
				pthread_setname_np(self->name);
				if(self->maxPriority){
					DarwinSpecific::SetCurrentThreadPriority(DarwinSpecific::THREAD_PRIO_USER_INTERACTIVE);
				}
#endif
			}
			self->entry();
			return NULL;
		}
		std::function<void()> entry;
		pthread_t thread;
		const char* name;
		bool maxPriority=false;
		bool valid=false;
	};
}

#ifdef __APPLE__
#include <dispatch/dispatch.h>
namespace tgvoip{
class Semaphore{
public:
	Semaphore(unsigned int maxCount, unsigned int initValue){
		sem = dispatch_semaphore_create(initValue);
	}
	
	~Semaphore(){
#if ! __has_feature(objc_arc)
        dispatch_release(sem);
#endif
	}
	
	void Acquire(){
		dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER);
	}
	
	void Release(){
		dispatch_semaphore_signal(sem);
	}
	
	void Acquire(int count){
		for(int i=0;i<count;i++)
			Acquire();
	}
	
	void Release(int count){
		for(int i=0;i<count;i++)
			Release();
	}
	
private:
	dispatch_semaphore_t sem;
};
}
#else
namespace tgvoip{
class Semaphore{
public:
	Semaphore(unsigned int maxCount, unsigned int initValue){
		sem_init(&sem, 0, initValue);
	}

	~Semaphore(){
		sem_destroy(&sem);
	}

	void Acquire(){
		sem_wait(&sem);
	}

	void Release(){
		sem_post(&sem);
	}

	void Acquire(int count){
		for(int i=0;i<count;i++)
			Acquire();
	}

	void Release(int count){
		for(int i=0;i<count;i++)
			Release();
	}

private:
	sem_t sem;
};
}
#endif

#elif defined(_WIN32)

#include <Windows.h>
#include <assert.h>

namespace tgvoip{
	class Mutex{
	public:
		Mutex(){
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY!=WINAPI_FAMILY_PHONE_APP
			InitializeCriticalSection(&section);
#else
			InitializeCriticalSectionEx(&section, 0, 0);
#endif
		}

		~Mutex(){
			DeleteCriticalSection(&section);
		}

		void Lock(){
			EnterCriticalSection(&section);
		}

		void Unlock(){
			LeaveCriticalSection(&section);
		}

	private:
		Mutex(const Mutex& other);
		CRITICAL_SECTION section;
	};

	class Thread{
	public:
		Thread(std::function<void()> entry) : entry(entry){
			name=NULL;
			thread=NULL;
		}

		~Thread(){
		}

		void Start(){
			thread=CreateThread(NULL, 0, Thread::ActualEntryPoint, this, 0, &id);
		}

		void Join(){
			if(!thread)
				return;
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY!=WINAPI_FAMILY_PHONE_APP
			WaitForSingleObject(thread, INFINITE);
#else
			WaitForSingleObjectEx(thread, INFINITE, false);
#endif
			CloseHandle(thread);
		}

		void SetName(const char* name){
			this->name=name;
		}

		void SetMaxPriority(){
			SetThreadPriority(thread, THREAD_PRIORITY_HIGHEST);
		}

		static void Sleep(double seconds){
			::Sleep((DWORD)(seconds*1000));
		}

		bool IsCurrent(){
			return id==GetCurrentThreadId();
		}

	private:
		static const DWORD MS_VC_EXCEPTION=0x406D1388;

		#pragma pack(push,8)
		typedef struct tagTHREADNAME_INFO
		{
		   DWORD dwType; // Must be 0x1000.
		   LPCSTR szName; // Pointer to name (in user addr space).
		   DWORD dwThreadID; // Thread ID (-1=caller thread).
		  DWORD dwFlags; // Reserved for future use, must be zero.
		} THREADNAME_INFO;
		#pragma pack(pop)

		static DWORD WINAPI ActualEntryPoint(void* arg){
			Thread* self=reinterpret_cast<Thread*>(arg);
			if(self->name){
				THREADNAME_INFO info;
				info.dwType=0x1000;
				info.szName=self->name;
				info.dwThreadID=-1;
				info.dwFlags=0;
				__try{
					RaiseException(MS_VC_EXCEPTION, 0, sizeof(info)/sizeof(ULONG_PTR), (ULONG_PTR*)&info);
				}__except(EXCEPTION_EXECUTE_HANDLER){}
			}
			self->entry();
			return 0;
		}
		std::function<void()> entry;
		HANDLE thread;
		DWORD id;
		const char* name;
	};

class Semaphore{
public:
	Semaphore(unsigned int maxCount, unsigned int initValue){
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY!=WINAPI_FAMILY_PHONE_APP
		h=CreateSemaphore(NULL, initValue, maxCount, NULL);
#else
		h=CreateSemaphoreEx(NULL, initValue, maxCount, NULL, 0, SEMAPHORE_ALL_ACCESS);
		assert(h);
#endif
	}

	~Semaphore(){
		CloseHandle(h);
	}

	void Acquire(){
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY!=WINAPI_FAMILY_PHONE_APP
		WaitForSingleObject(h, INFINITE);
#else
		WaitForSingleObjectEx(h, INFINITE, false);
#endif
	}

	void Release(){
		ReleaseSemaphore(h, 1, NULL);
	}

	void Acquire(int count){
		for(int i=0;i<count;i++)
			Acquire();
	}

	void Release(int count){
		ReleaseSemaphore(h, count, NULL);
	}

private:
	HANDLE h;
};
}
#else
#error "No threading implementation for your operating system"
#endif

namespace tgvoip{
class MutexGuard{
public:
    MutexGuard(Mutex &mutex) : mutex(mutex) {
		mutex.Lock();
	}
	~MutexGuard(){
		mutex.Unlock();
	}
private:
	Mutex &mutex;
};
}
	
#endif //__THREADING_H
