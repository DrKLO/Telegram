/*
	NOTE:
	I cannot get the vanilla getopt code to work (i.e. compile only what
	is needed and not duplicate symbols found in the standard library)
	on all the platforms that FLAC supports.  In particular the gating
	of code with the ELIDE_CODE #define is not accurate enough on systems
	that are POSIX but not glibc.  If someone has a patch that works on
	GNU/Linux, Darwin, AND Solaris please submit it on the project page:
		https://sourceforge.net/p/flac/patches/

	In the meantime I have munged the global symbols and removed gates
	around code, while at the same time trying to touch the original as
	little as possible.
*/
/* Declarations for getopt.
   Copyright (C) 1989,90,91,92,93,94,96,97,98 Free Software Foundation, Inc.
   This file is part of the GNU C Library.

   The GNU C Library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Library General Public License as
   published by the Free Software Foundation; either version 2 of the
   License, or (at your option) any later version.

   The GNU C Library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Library General Public License for more details.

   You should have received a copy of the GNU Library General Public
   License along with the GNU C Library; see the file COPYING.LIB.  If not,
   write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
   Boston, MA 02110-1301, USA.  */

#ifndef SHARE__GETOPT_H
#define SHARE__GETOPT_H

/*[JEC] was:#ifndef __need_getopt*/
/*[JEC] was:# define _GETOPT_H 1*/
/*[JEC] was:#endif*/

#ifdef	__cplusplus
extern "C" {
#endif

/* For communication from `share__getopt' to the caller.
   When `share__getopt' finds an option that takes an argument,
   the argument value is returned here.
   Also, when `ordering' is RETURN_IN_ORDER,
   each non-option ARGV-element is returned here.  */

extern char *share__optarg;

/* Index in ARGV of the next element to be scanned.
   This is used for communication to and from the caller
   and for communication between successive calls to `share__getopt'.

   On entry to `share__getopt', zero means this is the first call; initialize.

   When `share__getopt' returns -1, this is the index of the first of the
   non-option elements that the caller should itself scan.

   Otherwise, `share__optind' communicates from one call to the next
   how much of ARGV has been scanned so far.  */

extern int share__optind;

/* Callers store zero here to inhibit the error message `share__getopt' prints
   for unrecognized options.  */

extern int share__opterr;

/* Set to an option character which was unrecognized.  */

extern int share__optopt;

/*[JEC] was:#ifndef __need_getopt */
/* Describe the long-named options requested by the application.
   The LONG_OPTIONS argument to share__getopt_long or share__getopt_long_only is a vector
   of `struct share__option' terminated by an element containing a name which is
   zero.

   The field `has_arg' is:
   share__no_argument		(or 0) if the option does not take an argument,
   share__required_argument	(or 1) if the option requires an argument,
   share__optional_argument 	(or 2) if the option takes an optional argument.

   If the field `flag' is not NULL, it points to a variable that is set
   to the value given in the field `val' when the option is found, but
   left unchanged if the option is not found.

   To have a long-named option do something other than set an `int' to
   a compiled-in constant, such as set a value from `share__optarg', set the
   option's `flag' field to zero and its `val' field to a nonzero
   value (the equivalent single-letter option character, if there is
   one).  For long options that have a zero `flag' field, `share__getopt'
   returns the contents of the `val' field.  */

struct share__option
{
# if defined __STDC__ && __STDC__
  const char *name;
# else
  char *name;
# endif
  /* has_arg can't be an enum because some compilers complain about
     type mismatches in all the code that assumes it is an int.  */
  int has_arg;
  int *flag;
  int val;
};

/* Names for the values of the `has_arg' field of `struct share__option'.  */

# define share__no_argument		0
# define share__required_argument	1
# define share__optional_argument	2
/*[JEC] was:#endif*/	/* need getopt */


/* Get definitions and prototypes for functions to process the
   arguments in ARGV (ARGC of them, minus the program name) for
   options given in OPTS.

   Return the option character from OPTS just read.  Return -1 when
   there are no more options.  For unrecognized options, or options
   missing arguments, `share__optopt' is set to the option letter, and '?' is
   returned.

   The OPTS string is a list of characters which are recognized option
   letters, optionally followed by colons, specifying that that letter
   takes an argument, to be placed in `share__optarg'.

   If a letter in OPTS is followed by two colons, its argument is
   optional.  This behavior is specific to the GNU `share__getopt'.

   The argument `--' causes premature termination of argument
   scanning, explicitly telling `share__getopt' that there are no more
   options.

   If OPTS begins with `--', then non-option arguments are treated as
   arguments to the option '\0'.  This behavior is specific to the GNU
   `share__getopt'.  */

/*[JEC] was:#if defined __STDC__ && __STDC__*/
/*[JEC] was:# ifdef __GNU_LIBRARY__*/
/* Many other libraries have conflicting prototypes for getopt, with
   differences in the consts, in stdlib.h.  To avoid compilation
   errors, only prototype getopt for the GNU C library.  */
extern int share__getopt (int argc, char *const *argv, const char *shortopts);
/*[JEC] was:# else*/ /* not __GNU_LIBRARY__ */
/*[JEC] was:extern int getopt ();*/
/*[JEC] was:# endif*/ /* __GNU_LIBRARY__ */

/*[JEC] was:# ifndef __need_getopt*/
extern int share__getopt_long (int argc, char *const *argv, const char *shortopts,
		        const struct share__option *longopts, int *longind);
extern int share__getopt_long_only (int argc, char *const *argv,
			     const char *shortopts,
		             const struct share__option *longopts, int *longind);

/* Internal only.  Users should not call this directly.  */
extern int share___getopt_internal (int argc, char *const *argv,
			     const char *shortopts,
		             const struct share__option *longopts, int *longind,
			     int long_only);
/*[JEC] was:# endif*/
/*[JEC] was:#else*/ /* not __STDC__ */
/*[JEC] was:extern int getopt ();*/
/*[JEC] was:# ifndef __need_getopt*/
/*[JEC] was:extern int getopt_long ();*/
/*[JEC] was:extern int getopt_long_only ();*/

/*[JEC] was:extern int _getopt_internal ();*/
/*[JEC] was:# endif*/
/*[JEC] was:#endif*/ /* __STDC__ */

#ifdef	__cplusplus
}
#endif

/* Make sure we later can get all the definitions and declarations.  */
/*[JEC] was:#undef __need_getopt*/

#endif /* getopt.h */
