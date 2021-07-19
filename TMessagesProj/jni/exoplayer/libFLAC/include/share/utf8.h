#ifndef SHARE__UTF8_H
#define SHARE__UTF8_H

/*
 * Convert a string between UTF-8 and the locale's charset.
 * Invalid bytes are replaced by '#', and characters that are
 * not available in the target encoding are replaced by '?'.
 *
 * If the locale's charset is not set explicitly then it is
 * obtained using nl_langinfo(CODESET), where available, the
 * environment variable CHARSET, or assumed to be US-ASCII.
 *
 * Return value of conversion functions:
 *
 *  -1 : memory allocation failed
 *   0 : data was converted exactly
 *   1 : valid data was converted approximately (using '?')
 *   2 : input was invalid (but still converted, using '#')
 *   3 : unknown encoding (but still converted, using '?')
 */

int utf8_encode(const char *from, char **to);
int utf8_decode(const char *from, char **to);

#endif
