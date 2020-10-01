# PartitionAlloc Design

This document describes PartitionAlloc at a high level. For documentation about
its implementation, see the comments in `partition_alloc.h`.

[TOC]

## Overview

PartitionAlloc is a memory allocator optimized for security, low allocation
latency (when called appropriately), and good space efficiency (when called
appropriately). This document aims to help you understand how PartitionAlloc
works so that you can use it effectively.

## Partitions And Buckets

A *partition* is a heap that contains certain object types, objects of certain
sizes, or objects of a certain lifetime (as the caller prefers). Callers can
create as many partitions as they need. Each partition is separate and protected
from any other partitions.

Each partition holds multiple buckets. A *bucket* is a region in a partition
that contains similar-sized objects.

PartitionAlloc aligns each object allocation with the closest bucket size. For
example, if a partition has 3 buckets for 64 bytes, 256 bytes, and 1024 bytes,
then PartitionAlloc will satisfy an allocation request for 128 bytes by rounding
it up to 256 bytes and allocating from the second bucket.

The special allocator class `template <size_t N> class
SizeSpecificPartitionAllocator` will satisfy allocations only of size
`kMaxAllocation = N - kAllocationGranularity` or less, and contains buckets for
all `n * kAllocationGranularity` (n = 1, 2, ..., `kMaxAllocation`). Attempts to
allocate more than `kMaxAllocation` will fail.

## Performance

The current implementation is optimized for the main thread use-case. For
example, PartitionAlloc doesn't have threaded caches.

PartitionAlloc is designed to be extremely fast in its fast paths. The fast
paths of allocation and deallocation require just 2 (reasonably predictable)
branches. The number of operations in the fast paths is minimal, leading to the
possibility of inlining.

For an example of how to use partitions to get good performance and good safety,
see Blink's usage, as described in `wtf/allocator/Allocator.md`.

Large allocations (> kGenericMaxBucketed == 960KB) are realized by direct
memory mmapping. This size makes sense because 960KB = 0xF0000. The next larger
bucket size is 1MB = 0x100000 which is greater than 1/2 the available space in
a SuperPage meaning it would not be possible to pack even 2 sequential
allocations in a SuperPage.

`PartitionRootGeneric::Alloc()` acquires a lock for thread safety. (The current
implementation uses a spin lock on the assumption that thread contention will be
rare in its callers. The original caller was Blink, where this is generally
true. Spin locks also have the benefit of simplicity.)

Callers can get thread-unsafe performance using a
`SizeSpecificPartitionAllocator` or otherwise using `PartitionAlloc` (instead of
`PartitionRootGeneric::Alloc()`). Callers can also arrange for low contention,
such as by using a dedicated partition for single-threaded, latency-critical
allocations.

Because PartitionAlloc guarantees that address space regions used for one
partition are never reused for other partitions, partitions can eat a large
amount of virtual address space (even if not of actual memory).

Mixing various random objects in the same partition will generally lead to lower
efficiency. For good performance, group similar objects into the same partition.

## Security

Security is one of the most important goals of PartitionAlloc.

PartitionAlloc guarantees that different partitions exist in different regions
of the process' address space. When the caller has freed all objects contained
in a page in a partition, PartitionAlloc returns the physical memory to the
operating system, but continues to reserve the region of address space.
PartitionAlloc will only reuse an address space region for the same partition.

PartitionAlloc also guarantees that:

* Linear overflows cannot corrupt into the partition. (There is a guard page at
the beginning of each partition.)

* Linear overflows cannot corrupt out of the partition. (There is a guard page
at the end of each partition.)

* Linear overflow or underflow cannot corrupt the allocation metadata.
PartitionAlloc records metadata in a dedicated region out-of-line (not adjacent
to objects).

* Objects of different sizes will likely be allocated in different buckets, and
hence at different addresses. One page can contain only similar-sized objects.

* Dereference of a freelist pointer should fault.

* Partial pointer overwrite of freelist pointer should fault.

* Large allocations have guard pages at the beginning and end.
