#ifndef MODULES_THIRD_PARTY_PORTAUDIO_PA_RINGBUFFER_H_
#define MODULES_THIRD_PARTY_PORTAUDIO_PA_RINGBUFFER_H_
/*
 * $Id: pa_ringbuffer.h 1421 2009-11-18 16:09:05Z bjornroche $
 * Portable Audio I/O Library
 * Ring Buffer utility.
 *
 * Author: Phil Burk, http://www.softsynth.com
 * modified for SMP safety on OS X by Bjorn Roche.
 * also allowed for const where possible.
 * modified for multiple-byte-sized data elements by Sven Fischer
 *
 * Note that this is safe only for a single-thread reader
 * and a single-thread writer.
 *
 * This program is distributed with the PortAudio Portable Audio Library.
 * For more information see: http://www.portaudio.com
 * Copyright (c) 1999-2000 Ross Bencina and Phil Burk
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/*
 * The text above constitutes the entire PortAudio license; however,
 * the PortAudio community also makes the following non-binding requests:
 *
 * Any person wishing to distribute modifications to the Software is
 * requested to send the modifications to the original developer so that
 * they can be incorporated into the canonical version. It is also
 * requested that these non-binding requests be included along with the
 * license above.
 */

/** @file
 @ingroup common_src
 @brief Single-reader single-writer lock-free ring buffer

 PaUtilRingBuffer is a ring buffer used to transport samples between
 different execution contexts (threads, OS callbacks, interrupt handlers)
 without requiring the use of any locks. This only works when there is
 a single reader and a single writer (ie. one thread or callback writes
 to the ring buffer, another thread or callback reads from it).

 The PaUtilRingBuffer structure manages a ring buffer containing N
 elements, where N must be a power of two. An element may be any size
 (specified in bytes).

 The memory area used to store the buffer elements must be allocated by
 the client prior to calling PaUtil_InitializeRingBuffer() and must outlive
 the use of the ring buffer.
*/

#if defined(__APPLE__)
#include <sys/types.h>
typedef int32_t PaRingBufferSize;
#elif defined(__GNUC__)
typedef long PaRingBufferSize;
#elif (_MSC_VER >= 1400)
typedef long PaRingBufferSize;
#elif defined(_MSC_VER) || defined(__BORLANDC__)
typedef long PaRingBufferSize;
#else
typedef long PaRingBufferSize;
#endif

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct PaUtilRingBuffer {
  PaRingBufferSize bufferSize; /**< Number of elements in FIFO. Power of 2. Set
                                  by PaUtil_InitRingBuffer. */
  PaRingBufferSize writeIndex; /**< Index of next writable element. Set by
                                  PaUtil_AdvanceRingBufferWriteIndex. */
  PaRingBufferSize readIndex;  /**< Index of next readable element. Set by
                                  PaUtil_AdvanceRingBufferReadIndex. */
  PaRingBufferSize bigMask;    /**< Used for wrapping indices with extra bit to
                                  distinguish full/empty. */
  PaRingBufferSize smallMask;  /**< Used for fitting indices to buffer. */
  PaRingBufferSize elementSizeBytes; /**< Number of bytes per element. */
  char* buffer; /**< Pointer to the buffer containing the actual data. */
} PaUtilRingBuffer;

/** Initialize Ring Buffer.

 @param rbuf The ring buffer.

 @param elementSizeBytes The size of a single data element in bytes.

 @param elementCount The number of elements in the buffer (must be power of 2).

 @param dataPtr A pointer to a previously allocated area where the data
 will be maintained.  It must be elementCount*elementSizeBytes long.

 @return -1 if elementCount is not a power of 2, otherwise 0.
*/
PaRingBufferSize PaUtil_InitializeRingBuffer(PaUtilRingBuffer* rbuf,
                                             PaRingBufferSize elementSizeBytes,
                                             PaRingBufferSize elementCount,
                                             void* dataPtr);

/** Clear buffer. Should only be called when buffer is NOT being read.

 @param rbuf The ring buffer.
*/
void PaUtil_FlushRingBuffer(PaUtilRingBuffer* rbuf);

/** Retrieve the number of elements available in the ring buffer for writing.

 @param rbuf The ring buffer.

 @return The number of elements available for writing.
*/
PaRingBufferSize PaUtil_GetRingBufferWriteAvailable(PaUtilRingBuffer* rbuf);

/** Retrieve the number of elements available in the ring buffer for reading.

 @param rbuf The ring buffer.

 @return The number of elements available for reading.
*/
PaRingBufferSize PaUtil_GetRingBufferReadAvailable(PaUtilRingBuffer* rbuf);

/** Write data to the ring buffer.

 @param rbuf The ring buffer.

 @param data The address of new data to write to the buffer.

 @param elementCount The number of elements to be written.

 @return The number of elements written.
*/
PaRingBufferSize PaUtil_WriteRingBuffer(PaUtilRingBuffer* rbuf,
                                        const void* data,
                                        PaRingBufferSize elementCount);

/** Read data from the ring buffer.

 @param rbuf The ring buffer.

 @param data The address where the data should be stored.

 @param elementCount The number of elements to be read.

 @return The number of elements read.
*/
PaRingBufferSize PaUtil_ReadRingBuffer(PaUtilRingBuffer* rbuf,
                                       void* data,
                                       PaRingBufferSize elementCount);

/** Get address of region(s) to which we can write data.

 @param rbuf The ring buffer.

 @param elementCount The number of elements desired.

 @param dataPtr1 The address where the first (or only) region pointer will be
 stored.

 @param sizePtr1 The address where the first (or only) region length will be
 stored.

 @param dataPtr2 The address where the second region pointer will be stored if
 the first region is too small to satisfy elementCount.

 @param sizePtr2 The address where the second region length will be stored if
 the first region is too small to satisfy elementCount.

 @return The room available to be written or elementCount, whichever is smaller.
*/
PaRingBufferSize PaUtil_GetRingBufferWriteRegions(PaUtilRingBuffer* rbuf,
                                                  PaRingBufferSize elementCount,
                                                  void** dataPtr1,
                                                  PaRingBufferSize* sizePtr1,
                                                  void** dataPtr2,
                                                  PaRingBufferSize* sizePtr2);

/** Advance the write index to the next location to be written.

 @param rbuf The ring buffer.

 @param elementCount The number of elements to advance.

 @return The new position.
*/
PaRingBufferSize PaUtil_AdvanceRingBufferWriteIndex(
    PaUtilRingBuffer* rbuf,
    PaRingBufferSize elementCount);

/** Get address of region(s) from which we can write data.

 @param rbuf The ring buffer.

 @param elementCount The number of elements desired.

 @param dataPtr1 The address where the first (or only) region pointer will be
 stored.

 @param sizePtr1 The address where the first (or only) region length will be
 stored.

 @param dataPtr2 The address where the second region pointer will be stored if
 the first region is too small to satisfy elementCount.

 @param sizePtr2 The address where the second region length will be stored if
 the first region is too small to satisfy elementCount.

 @return The number of elements available for reading.
*/
PaRingBufferSize PaUtil_GetRingBufferReadRegions(PaUtilRingBuffer* rbuf,
                                                 PaRingBufferSize elementCount,
                                                 void** dataPtr1,
                                                 PaRingBufferSize* sizePtr1,
                                                 void** dataPtr2,
                                                 PaRingBufferSize* sizePtr2);

/** Advance the read index to the next location to be read.

 @param rbuf The ring buffer.

 @param elementCount The number of elements to advance.

 @return The new position.
*/
PaRingBufferSize PaUtil_AdvanceRingBufferReadIndex(
    PaUtilRingBuffer* rbuf,
    PaRingBufferSize elementCount);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* MODULES_THIRD_PARTY_PORTAUDIO_PA_RINGBUFFER_H_ */
