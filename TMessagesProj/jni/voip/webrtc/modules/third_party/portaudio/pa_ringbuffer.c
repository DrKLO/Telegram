/*
 * $Id: pa_ringbuffer.c 1421 2009-11-18 16:09:05Z bjornroche $
 * Portable Audio I/O Library
 * Ring Buffer utility.
 *
 * Author: Phil Burk, http://www.softsynth.com
 * modified for SMP safety on Mac OS X by Bjorn Roche
 * modified for SMP safety on Linux by Leland Lucius
 * also, allowed for const where possible
 * modified for multiple-byte-sized data elements by Sven Fischer 
 *
 * Note that this is safe only for a single-thread reader and a
 * single-thread writer.
 *
 * This program uses the PortAudio Portable Audio Library.
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

/**
 @file
 @ingroup common_src
*/

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "modules/third_party/portaudio/pa_memorybarrier.h"
#include "modules/third_party/portaudio/pa_ringbuffer.h"

/***************************************************************************
 * Initialize FIFO.
 * elementCount must be power of 2, returns -1 if not.
 */
PaRingBufferSize PaUtil_InitializeRingBuffer(PaUtilRingBuffer* rbuf,
                                             PaRingBufferSize elementSizeBytes,
                                             PaRingBufferSize elementCount,
                                             void* dataPtr) {
    if( ((elementCount-1) & elementCount) != 0) return -1; /* Not Power of two. */
    rbuf->bufferSize = elementCount;
    rbuf->buffer = (char *)dataPtr;
    PaUtil_FlushRingBuffer( rbuf );
    rbuf->bigMask = (elementCount*2)-1;
    rbuf->smallMask = (elementCount)-1;
    rbuf->elementSizeBytes = elementSizeBytes;
    return 0;
}

/***************************************************************************
** Return number of elements available for reading. */
PaRingBufferSize PaUtil_GetRingBufferReadAvailable( PaUtilRingBuffer *rbuf )
{
    PaUtil_ReadMemoryBarrier();
    return ( (rbuf->writeIndex - rbuf->readIndex) & rbuf->bigMask );
}
/***************************************************************************
** Return number of elements available for writing. */
PaRingBufferSize PaUtil_GetRingBufferWriteAvailable( PaUtilRingBuffer *rbuf )
{
    /* Since we are calling PaUtil_GetRingBufferReadAvailable, we don't need an aditional MB */
    return ( rbuf->bufferSize - PaUtil_GetRingBufferReadAvailable(rbuf));
}

/***************************************************************************
** Clear buffer. Should only be called when buffer is NOT being read. */
void PaUtil_FlushRingBuffer( PaUtilRingBuffer *rbuf )
{
    rbuf->writeIndex = rbuf->readIndex = 0;
}

/***************************************************************************
** Get address of region(s) to which we can write data.
** If the region is contiguous, size2 will be zero.
** If non-contiguous, size2 will be the size of second region.
** Returns room available to be written or elementCount, whichever is smaller.
*/
PaRingBufferSize PaUtil_GetRingBufferWriteRegions(PaUtilRingBuffer* rbuf,
                                                  PaRingBufferSize elementCount,
                                                  void** dataPtr1,
                                                  PaRingBufferSize* sizePtr1,
                                                  void** dataPtr2,
                                                  PaRingBufferSize* sizePtr2) {
    PaRingBufferSize   index;
    PaRingBufferSize   available = PaUtil_GetRingBufferWriteAvailable( rbuf );
    if( elementCount > available ) elementCount = available;
    /* Check to see if write is not contiguous. */
    index = rbuf->writeIndex & rbuf->smallMask;
    if( (index + elementCount) > rbuf->bufferSize )
    {
        /* Write data in two blocks that wrap the buffer. */
        PaRingBufferSize   firstHalf = rbuf->bufferSize - index;
        *dataPtr1 = &rbuf->buffer[index*rbuf->elementSizeBytes];
        *sizePtr1 = firstHalf;
        *dataPtr2 = &rbuf->buffer[0];
        *sizePtr2 = elementCount - firstHalf;
    }
    else
    {
        *dataPtr1 = &rbuf->buffer[index*rbuf->elementSizeBytes];
        *sizePtr1 = elementCount;
        *dataPtr2 = NULL;
        *sizePtr2 = 0;
    }
    return elementCount;
}


/***************************************************************************
*/
PaRingBufferSize PaUtil_AdvanceRingBufferWriteIndex(
    PaUtilRingBuffer* rbuf,
    PaRingBufferSize elementCount) {
    /* we need to ensure that previous writes are seen before we update the write index */
    PaUtil_WriteMemoryBarrier();
    return rbuf->writeIndex = (rbuf->writeIndex + elementCount) & rbuf->bigMask;
}

/***************************************************************************
** Get address of region(s) from which we can read data.
** If the region is contiguous, size2 will be zero.
** If non-contiguous, size2 will be the size of second region.
** Returns room available to be written or elementCount, whichever is smaller.
*/
PaRingBufferSize PaUtil_GetRingBufferReadRegions(PaUtilRingBuffer* rbuf,
                                                 PaRingBufferSize elementCount,
                                                 void** dataPtr1,
                                                 PaRingBufferSize* sizePtr1,
                                                 void** dataPtr2,
                                                 PaRingBufferSize* sizePtr2) {
    PaRingBufferSize   index;
    PaRingBufferSize   available = PaUtil_GetRingBufferReadAvailable( rbuf );
    if( elementCount > available ) elementCount = available;
    /* Check to see if read is not contiguous. */
    index = rbuf->readIndex & rbuf->smallMask;
    if( (index + elementCount) > rbuf->bufferSize )
    {
        /* Write data in two blocks that wrap the buffer. */
        PaRingBufferSize firstHalf = rbuf->bufferSize - index;
        *dataPtr1 = &rbuf->buffer[index*rbuf->elementSizeBytes];
        *sizePtr1 = firstHalf;
        *dataPtr2 = &rbuf->buffer[0];
        *sizePtr2 = elementCount - firstHalf;
    }
    else
    {
        *dataPtr1 = &rbuf->buffer[index*rbuf->elementSizeBytes];
        *sizePtr1 = elementCount;
        *dataPtr2 = NULL;
        *sizePtr2 = 0;
    }
    return elementCount;
}
/***************************************************************************
*/
PaRingBufferSize PaUtil_AdvanceRingBufferReadIndex(
    PaUtilRingBuffer* rbuf,
    PaRingBufferSize elementCount) {
    /* we need to ensure that previous writes are always seen before updating the index. */
    PaUtil_WriteMemoryBarrier();
    return rbuf->readIndex = (rbuf->readIndex + elementCount) & rbuf->bigMask;
}

/***************************************************************************
** Return elements written. */
PaRingBufferSize PaUtil_WriteRingBuffer(PaUtilRingBuffer* rbuf,
                                        const void* data,
                                        PaRingBufferSize elementCount) {
    PaRingBufferSize size1, size2, numWritten;
    void *data1, *data2;
    numWritten = PaUtil_GetRingBufferWriteRegions( rbuf, elementCount, &data1, &size1, &data2, &size2 );
    if( size2 > 0 )
    {

        memcpy( data1, data, size1*rbuf->elementSizeBytes );
        data = ((char *)data) + size1*rbuf->elementSizeBytes;
        memcpy( data2, data, size2*rbuf->elementSizeBytes );
    }
    else
    {
        memcpy( data1, data, size1*rbuf->elementSizeBytes );
    }
    PaUtil_AdvanceRingBufferWriteIndex( rbuf, numWritten );
    return numWritten;
}

/***************************************************************************
** Return elements read. */
PaRingBufferSize PaUtil_ReadRingBuffer(PaUtilRingBuffer* rbuf,
                                       void* data,
                                       PaRingBufferSize elementCount) {
    PaRingBufferSize size1, size2, numRead;
    void *data1, *data2;
    numRead = PaUtil_GetRingBufferReadRegions( rbuf, elementCount, &data1, &size1, &data2, &size2 );
    if( size2 > 0 )
    {
        memcpy( data, data1, size1*rbuf->elementSizeBytes );
        data = ((char *)data) + size1*rbuf->elementSizeBytes;
        memcpy( data, data2, size2*rbuf->elementSizeBytes );
    }
    else
    {
        memcpy( data, data1, size1*rbuf->elementSizeBytes );
    }
    PaUtil_AdvanceRingBufferReadIndex( rbuf, numRead );
    return numRead;
}
