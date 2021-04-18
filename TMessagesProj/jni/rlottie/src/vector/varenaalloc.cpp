/*
 * Copyright 2016 Google Inc.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include "varenaalloc.h"
#include <algorithm>
#include <new>

static char* end_chain(char*) { return nullptr; }

static uint32_t first_allocated_block(uint32_t blockSize, uint32_t firstHeapAllocation) {
    return firstHeapAllocation > 0 ? firstHeapAllocation :
           blockSize           > 0 ? blockSize           : 1024;
}

VArenaAlloc::VArenaAlloc(char* block, size_t size, size_t firstHeapAllocation)
    : fDtorCursor {block}
    , fCursor     {block}
    , fEnd        {block + ToU32(size)}
    , fFirstBlock {block}
    , fFirstSize  {ToU32(size)}
    , fFirstHeapAllocationSize  {first_allocated_block(ToU32(size), ToU32(firstHeapAllocation))}
{
    if (size < sizeof(Footer)) {
        fEnd = fCursor = fDtorCursor = nullptr;
    }

    if (fCursor != nullptr) {
        this->installFooter(end_chain, 0);
    }
}

VArenaAlloc::~VArenaAlloc() {
    RunDtorsOnBlock(fDtorCursor);
}

void VArenaAlloc::reset() {
    this->~VArenaAlloc();
    new (this) VArenaAlloc{fFirstBlock, fFirstSize, fFirstHeapAllocationSize};
}

void VArenaAlloc::installFooter(FooterAction* action, uint32_t padding) {
    assert(padding < 64);
    int64_t actionInt = (int64_t)(intptr_t)action;

    // The top 14 bits should be either all 0s or all 1s. Check this.
    assert((actionInt << 6) >> 6 == actionInt);
    Footer encodedFooter = (actionInt << 6) | padding;
    memmove(fCursor, &encodedFooter, sizeof(Footer));
    fCursor += sizeof(Footer);
    fDtorCursor = fCursor;
}

void VArenaAlloc::installPtrFooter(FooterAction* action, char* ptr, uint32_t padding) {
    memmove(fCursor, &ptr, sizeof(char*));
    fCursor += sizeof(char*);
    this->installFooter(action, padding);
}

char* VArenaAlloc::SkipPod(char* footerEnd) {
    char* objEnd = footerEnd - (sizeof(Footer) + sizeof(int32_t));
    int32_t skip;
    memmove(&skip, objEnd, sizeof(int32_t));
    return objEnd - skip;
}

void VArenaAlloc::RunDtorsOnBlock(char* footerEnd) {
    while (footerEnd != nullptr) {
        Footer footer;
        memcpy(&footer, footerEnd - sizeof(Footer), sizeof(Footer));

        FooterAction* action = (FooterAction*)(footer >> 6);
        ptrdiff_t padding = footer & 63;

        footerEnd = action(footerEnd) - padding;
    }
}

char* VArenaAlloc::NextBlock(char* footerEnd) {
    char* objEnd = footerEnd - (sizeof(Footer) + sizeof(char*));
    char* next;
    memmove(&next, objEnd, sizeof(char*));
    RunDtorsOnBlock(next);
    delete [] objEnd;
    return nullptr;
}

void VArenaAlloc::installUint32Footer(FooterAction* action, uint32_t value, uint32_t padding) {
    memmove(fCursor, &value, sizeof(uint32_t));
    fCursor += sizeof(uint32_t);
    this->installFooter(action, padding);
}

void VArenaAlloc::ensureSpace(uint32_t size, uint32_t alignment) {
    constexpr uint32_t headerSize = sizeof(Footer) + sizeof(ptrdiff_t);
    // The chrome c++ library we use does not define std::max_align_t.
    // This must be conservative to add the right amount of extra memory to handle the alignment
    // padding.
    constexpr uint32_t alignof_max_align_t = 8;
    constexpr uint32_t maxSize = std::numeric_limits<uint32_t>::max();
    constexpr uint32_t overhead = headerSize + sizeof(Footer);
    AssertRelease(size <= maxSize - overhead);
    uint32_t objSizeAndOverhead = size + overhead;
    if (alignment > alignof_max_align_t) {
        uint32_t alignmentOverhead = alignment - 1;
        AssertRelease(objSizeAndOverhead <= maxSize - alignmentOverhead);
        objSizeAndOverhead += alignmentOverhead;
    }

    uint32_t minAllocationSize;
    if (fFirstHeapAllocationSize <= maxSize / fFib0) {
        minAllocationSize = fFirstHeapAllocationSize * fFib0;
        fFib0 += fFib1;
        std::swap(fFib0, fFib1);
    } else {
        minAllocationSize = maxSize;
    }
    uint32_t allocationSize = std::max(objSizeAndOverhead, minAllocationSize);

    // Round up to a nice size. If > 32K align to 4K boundary else up to max_align_t. The > 32K
    // heuristic is from the JEMalloc behavior.
    {
        uint32_t mask = allocationSize > (1 << 15) ? (1 << 12) - 1 : 16 - 1;
        AssertRelease(allocationSize <= maxSize - mask);
        allocationSize = (allocationSize + mask) & ~mask;
    }

    char* newBlock = new char[allocationSize];

    auto previousDtor = fDtorCursor;
    fCursor = newBlock;
    fDtorCursor = newBlock;
    fEnd = fCursor + allocationSize;
    this->installPtrFooter(NextBlock, previousDtor, 0);
}

char* VArenaAlloc::allocObjectWithFooter(uint32_t sizeIncludingFooter, uint32_t alignment) {
    uintptr_t mask = alignment - 1;

restart:
    uint32_t skipOverhead = 0;
    bool needsSkipFooter = fCursor != fDtorCursor;
    if (needsSkipFooter) {
        skipOverhead = sizeof(Footer) + sizeof(uint32_t);
    }
    char* objStart = (char*)((uintptr_t)(fCursor + skipOverhead + mask) & ~mask);
    uint32_t totalSize = sizeIncludingFooter + skipOverhead;
    //std::cout<<"non POD object size = "<<totalSize<<"\n";
    if ((ptrdiff_t)totalSize > fEnd - objStart) {
        this->ensureSpace(totalSize, alignment);
        goto restart;
    }

    AssertRelease((ptrdiff_t)totalSize <= fEnd - objStart);

    // Install a skip footer if needed, thus terminating a run of POD data. The calling code is
    // responsible for installing the footer after the object.
    if (needsSkipFooter) {
        this->installUint32Footer(SkipPod, ToU32(fCursor - fDtorCursor), 0);
    }

    return objStart;
}
