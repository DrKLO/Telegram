/* FLAC - Free Lossless Audio Codec
 * Copyright (C) 2001-2009  Josh Coalson
 * Copyright (C) 2011-2014  Xiph.Org Foundation
 *
 * This file is part the FLAC project.  FLAC is comprised of several
 * components distributed under different licenses.  The codec libraries
 * are distributed under Xiph.Org's BSD-like license (see the file
 * COPYING.Xiph in this distribution).  All other programs, libraries, and
 * plugins are distributed under the LGPL or GPL (see COPYING.LGPL and
 * COPYING.GPL).  The documentation is distributed under the Gnu FDL (see
 * COPYING.FDL).  Each file in the FLAC distribution contains at the top the
 * terms under which it may be distributed.
 *
 * Since this particular file is relevant to all components of FLAC,
 * it may be distributed under the Xiph.Org license, which is the least
 * restrictive of those mentioned above.  See the file COPYING.Xiph in this
 * distribution.
 */


FLAC is an Open Source lossless audio codec developed by Josh Coalson from 2001
to 2009.

From January 2012 FLAC is being maintained by Erik de Castro Lopo under the
auspices of the Xiph.org Foundation.

FLAC is comprised of
  * `libFLAC', a library which implements reference encoders and
    decoders for native FLAC and Ogg FLAC, and a metadata interface
  * `libFLAC++', a C++ object wrapper library around libFLAC
  * `flac', a command-line program for encoding and decoding files
  * `metaflac', a command-line program for viewing and editing FLAC
    metadata
  * player plugin for XMMS
  * user and API documentation

The libraries (libFLAC, libFLAC++) are
licensed under Xiph.org's BSD-like license (see COPYING.Xiph).  All other
programs and plugins are licensed under the GNU General Public License
(see COPYING.GPL).  The documentation is licensed under the GNU Free
Documentation License (see COPYING.FDL).


===============================================================================
FLAC - 1.3.1 - Contents
===============================================================================

- Introduction
- Prerequisites
- Note to embedded developers
- Building in a GNU environment
- Building with Makefile.lite
- Building with MSVC
- Building on Mac OS X


===============================================================================
Introduction
===============================================================================

This is the source release for the FLAC project.  See

	doc/html/index.html

for full documentation.

A brief description of the directory tree:

	doc/          the HTML documentation
	examples/     example programs demonstrating the use of libFLAC and libFLAC++
	include/      public include files for libFLAC and libFLAC++
	man/          the man pages for `flac' and `metaflac'
	src/          the source code and private headers
	test/         the test scripts

If you have questions about building FLAC that this document does not answer,
please submit them at the following tracker so this document can be improved:

	https://sourceforge.net/p/flac/support-requests/


===============================================================================
Prerequisites
===============================================================================

To build FLAC with support for Ogg FLAC you must have built and installed
libogg according to the specific instructions below.  You must have
libogg 1.1.2 or greater, or there will be seeking problems with Ogg FLAC.

If you are building on x86 and want the assembly optimizations, you will
need to have NASM >= 0.98.30 installed according to the specific instructions
below.


===============================================================================
Note to embedded developers
===============================================================================

libFLAC has grown larger over time as more functionality has been
included, but much of it may be unnecessary for a particular embedded
implementation.  Unused parts may be pruned by some simple editing of
configure.ac and src/libFLAC/Makefile.am; the following dependency
graph shows which modules may be pruned without breaking things
further down:

metadata.h
	stream_decoder.h
	format.h

stream_encoder.h
	stream_decoder.h
	format.h

stream_decoder.h
	format.h

In other words, for pure decoding applications, both the stream encoder
and metadata editing interfaces can be safely removed.

There is a section dedicated to embedded use in the libFLAC API
HTML documentation (see doc/html/api/index.html).

Also, there are several places in the libFLAC code with comments marked
with "OPT:" where a #define can be changed to enable code that might be
faster on a specific platform.  Experimenting with these can yield faster
binaries.


===============================================================================
Building in a GNU environment
===============================================================================

FLAC uses autoconf and libtool for configuring and building.
Better documentation for these will be forthcoming, but in
general, this should work:

./configure && make && make check && make install

The 'make check' step is optional; omit it to skip all the tests,
which can take several hours and use around 70-80 megs of disk space.
Even though it will stop with an explicit message on any failure, it
does print out a lot of stuff so you might want to capture the output
to a file if you're having a problem.  Also, don't run 'make check'
as root because it confuses some of the tests.

NOTE: Despite our best efforts it's entirely possible to have
problems when using older versions of autoconf, automake, or
libtool.  If you have the latest versions and still can't get it
to work, see the next section on Makefile.lite.

There are a few FLAC-specific arguments you can give to
`configure':

--enable-debug : Builds everything with debug symbols and some
extra (and more verbose) error checking.

--disable-asm-optimizations : Disables the compilation of the
assembly routines.  Many routines have assembly versions for
speed and `configure' is pretty good about knowing what is
supported, but you can use this option to build only from the
C sources.  May be necessary for building on OS X (Intel).

--enable-sse : If you are building for an x86 CPU that supports
SSE instructions, you can enable some of the faster routines
if your operating system also supports SSE instructions.  flac
can tell if the CPU supports the instructions but currently has
no way to test if the OS does, so if it does, you must pass
this argument to configure to use the SSE routines.  If flac
crashes when built with this option you will have to go back and
configure without --enable-sse.  Note that
--disable-asm-optimizations implies --disable-sse.

--enable-local-xmms-plugin : Installs the FLAC XMMS plugin in
$HOME/.xmms/Plugins, instead of the global XMMS plugin area
(usually /usr/lib/xmms/Input).

--with-ogg=
--with-xmms-prefix=
--with-libiconv-prefix=
Use these if you have these packages but configure can't find them.

If you want to build completely from scratch (i.e. starting with just
configure.ac and Makefile.am) you should be able to just run 'autogen.sh'
but make sure and read the comments in that file first.


===============================================================================
Building with Makefile.lite
===============================================================================

There is a more lightweight build system for do-it-yourself-ers.
It is also useful if configure isn't working, which may be the
case since lately we've had some problems with different versions
of automake and libtool.  The Makefile.lite system should work
on GNU systems with few or no adjustments.

From the top level just 'make -f Makefile.lite'.  You can
specify zero or one optional target from 'release', 'debug',
'test', or 'clean'.  The default is 'release'.  There is no
'install' target but everything you need will end up in the
obj/ directory.

If you are not on an x86 system or you don't have nasm, you
may have to change the DEFINES in src/libFLAC/Makefile.lite.  If
you don't have nasm, remove -DFLAC__HAS_NASM.  If your target is
not an x86, change -DFLAC__CPU_IA32 to -DFLAC__CPU_UNKNOWN.


===============================================================================
Building with MSVC
===============================================================================

There are .vcproj projects and a master FLAC.sln solution to build all
the libraries and executables with MSVC 2005 or newer.

Prerequisite: you must have the Ogg libraries installed as described
later.

Prerequisite: you must have nasm installed, and nasm.exe must be in
your PATH, or the path to nasm.exe must be added to the list of
directories for executable files in the MSVC global options.

To build everything, run Visual Studio, do File|Open and open FLAC.sln.
From the dropdown in the toolbar, select "Release" instead of "Debug",
then do Build|Build Solution.

This will build all libraries both statically (e.g.
objs\release\lib\libFLAC_static.lib) and as DLLs (e.g.
objs\release\lib\libFLAC.dll), and it will build all binaries, statically
linked (e.g. objs\release\bin\flac.exe).

Everything will end up in the "objs" directory.  DLLs and .exe files
are all that are needed and can be copied to an installation area and
added to the PATH.

By default the code is configured with Ogg support. Before building FLAC
you will need to get the Ogg source distribution
(see http://xiph.org/downloads/), build libogg_static.lib (load
win32\libogg_static.sln, change solution configuration to "Release" and
code generation to "Multi-threaded (/MT)", then build), copy libogg_static.lib
into FLAC's 'objs\release\lib' directory, and copy the entire include\ogg tree
into FLAC's 'include' directory (so that there is an 'ogg' directory in FLAC's
'include' directory with the files ogg.h, os_types.h and config_types.h).

If you want to build without Ogg support, instead edit all .vcproj files
and remove any "FLAC__HAS_OGG" definitions.


===============================================================================
Building on Mac OS X
===============================================================================

If you have Fink or a recent version of OS X with the proper autotools,
the GNU flow above should work.
