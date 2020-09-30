/*
 * Copyright (C)2011-2019 D. R. Commander.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of the libjpeg-turbo Project nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS",
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#include <stdlib.h>
#include <string.h>
#include "turbojpeg.h"
#ifdef WIN32
#include "tjutil.h"
#endif
#include <jni.h>
#include "java/org_libjpegturbo_turbojpeg_TJCompressor.h"
#include "java/org_libjpegturbo_turbojpeg_TJDecompressor.h"
#include "java/org_libjpegturbo_turbojpeg_TJTransformer.h"
#include "java/org_libjpegturbo_turbojpeg_TJ.h"

#define BAILIF0(f) { \
  if (!(f) || (*env)->ExceptionCheck(env)) { \
    goto bailout; \
  } \
}

#define THROW(msg, exceptionClass) { \
  jclass _exccls = (*env)->FindClass(env, exceptionClass); \
  \
  BAILIF0(_exccls); \
  (*env)->ThrowNew(env, _exccls, msg); \
  goto bailout; \
}

#define THROW_TJ() { \
  jclass _exccls; \
  jmethodID _excid; \
  jobject _excobj; \
  jstring _errstr; \
  \
  BAILIF0(_errstr = (*env)->NewStringUTF(env, tjGetErrorStr2(handle))); \
  BAILIF0(_exccls = (*env)->FindClass(env, \
    "org/libjpegturbo/turbojpeg/TJException")); \
  BAILIF0(_excid = (*env)->GetMethodID(env, _exccls, "<init>", \
                                       "(Ljava/lang/String;I)V")); \
  BAILIF0(_excobj = (*env)->NewObject(env, _exccls, _excid, _errstr, \
                                      tjGetErrorCode(handle))); \
  (*env)->Throw(env, _excobj); \
  goto bailout; \
}

#define THROW_ARG(msg)  THROW(msg, "java/lang/IllegalArgumentException")

#define THROW_MEM() \
  THROW("Memory allocation failure", "java/lang/OutOfMemoryError");

#define GET_HANDLE() \
  jclass _cls = (*env)->GetObjectClass(env, obj); \
  jfieldID _fid; \
  \
  BAILIF0(_cls); \
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "handle", "J")); \
  handle = (tjhandle)(size_t)(*env)->GetLongField(env, obj, _fid);

#ifdef _WIN32
#define setenv(envvar, value, dummy)  _putenv_s(envvar, value)
#endif

#define PROP2ENV(property, envvar) { \
  if ((jName = (*env)->NewStringUTF(env, property)) != NULL && \
      (jValue = (*env)->CallStaticObjectMethod(env, cls, mid, \
                                               jName)) != NULL) { \
    if ((value = (*env)->GetStringUTFChars(env, jValue, 0)) != NULL) { \
      setenv(envvar, value, 1); \
      (*env)->ReleaseStringUTFChars(env, jValue, value); \
    } \
  } \
}

#define SAFE_RELEASE(javaArray, cArray) { \
  if (javaArray && cArray) \
    (*env)->ReleasePrimitiveArrayCritical(env, javaArray, (void *)cArray, 0); \
  cArray = NULL; \
}

static int ProcessSystemProperties(JNIEnv *env)
{
  jclass cls;
  jmethodID mid;
  jstring jName, jValue;
  const char *value;

  BAILIF0(cls = (*env)->FindClass(env, "java/lang/System"));
  BAILIF0(mid = (*env)->GetStaticMethodID(env, cls, "getProperty",
    "(Ljava/lang/String;)Ljava/lang/String;"));

  PROP2ENV("turbojpeg.optimize", "TJ_OPTIMIZE");
  PROP2ENV("turbojpeg.arithmetic", "TJ_ARITHMETIC");
  PROP2ENV("turbojpeg.restart", "TJ_RESTART");
  PROP2ENV("turbojpeg.progressive", "TJ_PROGRESSIVE");
  return 0;

bailout:
  return -1;
}

/* TurboJPEG 1.2.x: TJ::bufSize() */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJ_bufSize
  (JNIEnv *env, jclass cls, jint width, jint height, jint jpegSubsamp)
{
  jint retval = (jint)tjBufSize(width, height, jpegSubsamp);

  if (retval == -1) THROW_ARG(tjGetErrorStr());

bailout:
  return retval;
}

/* TurboJPEG 1.4.x: TJ::bufSizeYUV() */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJ_bufSizeYUV__IIII
  (JNIEnv *env, jclass cls, jint width, jint pad, jint height, jint subsamp)
{
  jint retval = (jint)tjBufSizeYUV2(width, pad, height, subsamp);

  if (retval == -1) THROW_ARG(tjGetErrorStr());

bailout:
  return retval;
}

/* TurboJPEG 1.2.x: TJ::bufSizeYUV() */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJ_bufSizeYUV__III
  (JNIEnv *env, jclass cls, jint width, jint height, jint subsamp)
{
  return Java_org_libjpegturbo_turbojpeg_TJ_bufSizeYUV__IIII(env, cls, width,
                                                             4, height,
                                                             subsamp);
}

/* TurboJPEG 1.4.x: TJ::planeSizeYUV() */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJ_planeSizeYUV__IIIII
  (JNIEnv *env, jclass cls, jint componentID, jint width, jint stride,
   jint height, jint subsamp)
{
  jint retval = (jint)tjPlaneSizeYUV(componentID, width, stride, height,
                                     subsamp);

  if (retval == -1) THROW_ARG(tjGetErrorStr());

bailout:
  return retval;
}

/* TurboJPEG 1.4.x: TJ::planeWidth() */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJ_planeWidth__III
  (JNIEnv *env, jclass cls, jint componentID, jint width, jint subsamp)
{
  jint retval = (jint)tjPlaneWidth(componentID, width, subsamp);

  if (retval == -1) THROW_ARG(tjGetErrorStr());

bailout:
  return retval;
}

/* TurboJPEG 1.4.x: TJ::planeHeight() */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJ_planeHeight__III
  (JNIEnv *env, jclass cls, jint componentID, jint height, jint subsamp)
{
  jint retval = (jint)tjPlaneHeight(componentID, height, subsamp);

  if (retval == -1) THROW_ARG(tjGetErrorStr());

bailout:
  return retval;
}

/* TurboJPEG 1.2.x: TJCompressor::init() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_init
  (JNIEnv *env, jobject obj)
{
  jclass cls;
  jfieldID fid;
  tjhandle handle;

  if ((handle = tjInitCompress()) == NULL)
    THROW(tjGetErrorStr(), "org/libjpegturbo/turbojpeg/TJException");

  BAILIF0(cls = (*env)->GetObjectClass(env, obj));
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "handle", "J"));
  (*env)->SetLongField(env, obj, fid, (size_t)handle);

bailout:
  return;
}

static jint TJCompressor_compress
  (JNIEnv *env, jobject obj, jarray src, jint srcElementSize, jint x, jint y,
   jint width, jint pitch, jint height, jint pf, jbyteArray dst,
   jint jpegSubsamp, jint jpegQual, jint flags)
{
  tjhandle handle = 0;
  unsigned long jpegSize = 0;
  jsize arraySize = 0, actualPitch;
  unsigned char *srcBuf = NULL, *jpegBuf = NULL;

  GET_HANDLE();

  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF || width < 1 ||
      height < 1 || pitch < 0)
    THROW_ARG("Invalid argument in compress()");
  if (org_libjpegturbo_turbojpeg_TJ_NUMPF != TJ_NUMPF)
    THROW_ARG("Mismatch between Java and C API");

  actualPitch = (pitch == 0) ? width * tjPixelSize[pf] : pitch;
  arraySize = (y + height - 1) * actualPitch + (x + width) * tjPixelSize[pf];
  if ((*env)->GetArrayLength(env, src) * srcElementSize < arraySize)
    THROW_ARG("Source buffer is not large enough");
  jpegSize = tjBufSize(width, height, jpegSubsamp);
  if ((*env)->GetArrayLength(env, dst) < (jsize)jpegSize)
    THROW_ARG("Destination buffer is not large enough");

  if (ProcessSystemProperties(env) < 0) goto bailout;

  BAILIF0(srcBuf = (*env)->GetPrimitiveArrayCritical(env, src, 0));
  BAILIF0(jpegBuf = (*env)->GetPrimitiveArrayCritical(env, dst, 0));

  if (tjCompress2(handle, &srcBuf[y * actualPitch + x * tjPixelSize[pf]],
                  width, pitch, height, pf, &jpegBuf, &jpegSize, jpegSubsamp,
                  jpegQual, flags | TJFLAG_NOREALLOC) == -1) {
    SAFE_RELEASE(dst, jpegBuf);
    SAFE_RELEASE(src, srcBuf);
    THROW_TJ();
  }

bailout:
  SAFE_RELEASE(dst, jpegBuf);
  SAFE_RELEASE(src, srcBuf);
  return (jint)jpegSize;
}

/* TurboJPEG 1.3.x: TJCompressor::compress() byte source */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_compress___3BIIIIII_3BIII
  (JNIEnv *env, jobject obj, jbyteArray src, jint x, jint y, jint width,
   jint pitch, jint height, jint pf, jbyteArray dst, jint jpegSubsamp,
   jint jpegQual, jint flags)
{
  return TJCompressor_compress(env, obj, src, 1, x, y, width, pitch, height,
                               pf, dst, jpegSubsamp, jpegQual, flags);
}

/* TurboJPEG 1.2.x: TJCompressor::compress() byte source */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_compress___3BIIII_3BIII
  (JNIEnv *env, jobject obj, jbyteArray src, jint width, jint pitch,
   jint height, jint pf, jbyteArray dst, jint jpegSubsamp, jint jpegQual,
   jint flags)
{
  return TJCompressor_compress(env, obj, src, 1, 0, 0, width, pitch, height,
                               pf, dst, jpegSubsamp, jpegQual, flags);
}

/* TurboJPEG 1.3.x: TJCompressor::compress() int source */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_compress___3IIIIIII_3BIII
  (JNIEnv *env, jobject obj, jintArray src, jint x, jint y, jint width,
   jint stride, jint height, jint pf, jbyteArray dst, jint jpegSubsamp,
   jint jpegQual, jint flags)
{
  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in compress()");
  if (tjPixelSize[pf] != sizeof(jint))
    THROW_ARG("Pixel format must be 32-bit when compressing from an integer buffer.");

  return TJCompressor_compress(env, obj, src, sizeof(jint), x, y, width,
                               stride * sizeof(jint), height, pf, dst,
                               jpegSubsamp, jpegQual, flags);

bailout:
  return 0;
}

/* TurboJPEG 1.2.x: TJCompressor::compress() int source */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_compress___3IIIII_3BIII
  (JNIEnv *env, jobject obj, jintArray src, jint width, jint stride,
   jint height, jint pf, jbyteArray dst, jint jpegSubsamp, jint jpegQual,
   jint flags)
{
  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in compress()");
  if (tjPixelSize[pf] != sizeof(jint))
    THROW_ARG("Pixel format must be 32-bit when compressing from an integer buffer.");

  return TJCompressor_compress(env, obj, src, sizeof(jint), 0, 0, width,
                               stride * sizeof(jint), height, pf, dst,
                               jpegSubsamp, jpegQual, flags);

bailout:
  return 0;
}

/* TurboJPEG 1.4.x: TJCompressor::compressFromYUV() */
JNIEXPORT jint JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_compressFromYUV___3_3B_3II_3III_3BII
  (JNIEnv *env, jobject obj, jobjectArray srcobjs, jintArray jSrcOffsets,
   jint width, jintArray jSrcStrides, jint height, jint subsamp,
   jbyteArray dst, jint jpegQual, jint flags)
{
  tjhandle handle = 0;
  unsigned long jpegSize = 0;
  jbyteArray jSrcPlanes[3] = { NULL, NULL, NULL };
  const unsigned char *srcPlanes[3];
  unsigned char *jpegBuf = NULL;
  int *srcOffsets = NULL, *srcStrides = NULL;
  int nc = (subsamp == org_libjpegturbo_turbojpeg_TJ_SAMP_GRAY ? 1 : 3), i;

  GET_HANDLE();

  if (subsamp < 0 || subsamp >= org_libjpegturbo_turbojpeg_TJ_NUMSAMP)
    THROW_ARG("Invalid argument in compressFromYUV()");
  if (org_libjpegturbo_turbojpeg_TJ_NUMSAMP != TJ_NUMSAMP)
    THROW_ARG("Mismatch between Java and C API");

  if ((*env)->GetArrayLength(env, srcobjs) < nc)
    THROW_ARG("Planes array is too small for the subsampling type");
  if ((*env)->GetArrayLength(env, jSrcOffsets) < nc)
    THROW_ARG("Offsets array is too small for the subsampling type");
  if ((*env)->GetArrayLength(env, jSrcStrides) < nc)
    THROW_ARG("Strides array is too small for the subsampling type");

  jpegSize = tjBufSize(width, height, subsamp);
  if ((*env)->GetArrayLength(env, dst) < (jsize)jpegSize)
    THROW_ARG("Destination buffer is not large enough");

  if (ProcessSystemProperties(env) < 0) goto bailout;

#define RELEASE_ARRAYS_COMPRESSFROMYUV() { \
  SAFE_RELEASE(dst, jpegBuf); \
  for (i = 0; i < nc; i++) \
    SAFE_RELEASE(jSrcPlanes[i], srcPlanes[i]); \
  SAFE_RELEASE(jSrcStrides, srcStrides); \
  SAFE_RELEASE(jSrcOffsets, srcOffsets); \
}

  BAILIF0(srcOffsets = (*env)->GetPrimitiveArrayCritical(env, jSrcOffsets, 0));
  BAILIF0(srcStrides = (*env)->GetPrimitiveArrayCritical(env, jSrcStrides, 0));
  for (i = 0; i < nc; i++) {
    int planeSize = tjPlaneSizeYUV(i, width, srcStrides[i], height, subsamp);
    int pw = tjPlaneWidth(i, width, subsamp);

    if (planeSize < 0 || pw < 0) {
      RELEASE_ARRAYS_COMPRESSFROMYUV();
      THROW_ARG(tjGetErrorStr());
    }

    if (srcOffsets[i] < 0) {
      RELEASE_ARRAYS_COMPRESSFROMYUV();
      THROW_ARG("Invalid argument in compressFromYUV()");
    }
    if (srcStrides[i] < 0 && srcOffsets[i] - planeSize + pw < 0) {
      RELEASE_ARRAYS_COMPRESSFROMYUV();
      THROW_ARG("Negative plane stride would cause memory to be accessed below plane boundary");
    }

    BAILIF0(jSrcPlanes[i] = (*env)->GetObjectArrayElement(env, srcobjs, i));
    if ((*env)->GetArrayLength(env, jSrcPlanes[i]) <
        srcOffsets[i] + planeSize) {
      RELEASE_ARRAYS_COMPRESSFROMYUV();
      THROW_ARG("Source plane is not large enough");
    }

    BAILIF0(srcPlanes[i] =
            (*env)->GetPrimitiveArrayCritical(env, jSrcPlanes[i], 0));
    srcPlanes[i] = &srcPlanes[i][srcOffsets[i]];
  }
  BAILIF0(jpegBuf = (*env)->GetPrimitiveArrayCritical(env, dst, 0));

  if (tjCompressFromYUVPlanes(handle, srcPlanes, width, srcStrides, height,
                              subsamp, &jpegBuf, &jpegSize, jpegQual,
                              flags | TJFLAG_NOREALLOC) == -1) {
    RELEASE_ARRAYS_COMPRESSFROMYUV();
    THROW_TJ();
  }

bailout:
  RELEASE_ARRAYS_COMPRESSFROMYUV();
  return (jint)jpegSize;
}

static void TJCompressor_encodeYUV
  (JNIEnv *env, jobject obj, jarray src, jint srcElementSize, jint x, jint y,
   jint width, jint pitch, jint height, jint pf, jobjectArray dstobjs,
   jintArray jDstOffsets, jintArray jDstStrides, jint subsamp, jint flags)
{
  tjhandle handle = 0;
  jsize arraySize = 0, actualPitch;
  jbyteArray jDstPlanes[3] = { NULL, NULL, NULL };
  unsigned char *srcBuf = NULL, *dstPlanes[3];
  int *dstOffsets = NULL, *dstStrides = NULL;
  int nc = (subsamp == org_libjpegturbo_turbojpeg_TJ_SAMP_GRAY ? 1 : 3), i;

  GET_HANDLE();

  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF || width < 1 ||
      height < 1 || pitch < 0 || subsamp < 0 ||
      subsamp >= org_libjpegturbo_turbojpeg_TJ_NUMSAMP)
    THROW_ARG("Invalid argument in encodeYUV()");
  if (org_libjpegturbo_turbojpeg_TJ_NUMPF != TJ_NUMPF ||
      org_libjpegturbo_turbojpeg_TJ_NUMSAMP != TJ_NUMSAMP)
    THROW_ARG("Mismatch between Java and C API");

  if ((*env)->GetArrayLength(env, dstobjs) < nc)
    THROW_ARG("Planes array is too small for the subsampling type");
  if ((*env)->GetArrayLength(env, jDstOffsets) < nc)
    THROW_ARG("Offsets array is too small for the subsampling type");
  if ((*env)->GetArrayLength(env, jDstStrides) < nc)
    THROW_ARG("Strides array is too small for the subsampling type");

  actualPitch = (pitch == 0) ? width * tjPixelSize[pf] : pitch;
  arraySize = (y + height - 1) * actualPitch + (x + width) * tjPixelSize[pf];
  if ((*env)->GetArrayLength(env, src) * srcElementSize < arraySize)
    THROW_ARG("Source buffer is not large enough");

#define RELEASE_ARRAYS_ENCODEYUV() { \
  SAFE_RELEASE(src, srcBuf); \
  for (i = 0; i < nc; i++) \
    SAFE_RELEASE(jDstPlanes[i], dstPlanes[i]); \
  SAFE_RELEASE(jDstStrides, dstStrides); \
  SAFE_RELEASE(jDstOffsets, dstOffsets); \
}

  BAILIF0(dstOffsets = (*env)->GetPrimitiveArrayCritical(env, jDstOffsets, 0));
  BAILIF0(dstStrides = (*env)->GetPrimitiveArrayCritical(env, jDstStrides, 0));
  for (i = 0; i < nc; i++) {
    int planeSize = tjPlaneSizeYUV(i, width, dstStrides[i], height, subsamp);
    int pw = tjPlaneWidth(i, width, subsamp);

    if (planeSize < 0 || pw < 0) {
      RELEASE_ARRAYS_ENCODEYUV();
      THROW_ARG(tjGetErrorStr());
    }

    if (dstOffsets[i] < 0) {
      RELEASE_ARRAYS_ENCODEYUV();
      THROW_ARG("Invalid argument in encodeYUV()");
    }
    if (dstStrides[i] < 0 && dstOffsets[i] - planeSize + pw < 0) {
      RELEASE_ARRAYS_ENCODEYUV();
      THROW_ARG("Negative plane stride would cause memory to be accessed below plane boundary");
    }

    BAILIF0(jDstPlanes[i] = (*env)->GetObjectArrayElement(env, dstobjs, i));
    if ((*env)->GetArrayLength(env, jDstPlanes[i]) <
        dstOffsets[i] + planeSize) {
      RELEASE_ARRAYS_ENCODEYUV();
      THROW_ARG("Destination plane is not large enough");
    }

    BAILIF0(dstPlanes[i] =
            (*env)->GetPrimitiveArrayCritical(env, jDstPlanes[i], 0));
    dstPlanes[i] = &dstPlanes[i][dstOffsets[i]];
  }
  BAILIF0(srcBuf = (*env)->GetPrimitiveArrayCritical(env, src, 0));

  if (tjEncodeYUVPlanes(handle, &srcBuf[y * actualPitch + x * tjPixelSize[pf]],
                        width, pitch, height, pf, dstPlanes, dstStrides,
                        subsamp, flags) == -1) {
    RELEASE_ARRAYS_ENCODEYUV();
    THROW_TJ();
  }

bailout:
  RELEASE_ARRAYS_ENCODEYUV();
}

/* TurboJPEG 1.4.x: TJCompressor::encodeYUV() byte source */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_encodeYUV___3BIIIIII_3_3B_3I_3III
  (JNIEnv *env, jobject obj, jbyteArray src, jint x, jint y, jint width,
   jint pitch, jint height, jint pf, jobjectArray dstobjs,
   jintArray jDstOffsets, jintArray jDstStrides, jint subsamp, jint flags)
{
  TJCompressor_encodeYUV(env, obj, src, 1, x, y, width, pitch, height, pf,
                         dstobjs, jDstOffsets, jDstStrides, subsamp, flags);
}

/* TurboJPEG 1.4.x: TJCompressor::encodeYUV() int source */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_encodeYUV___3IIIIIII_3_3B_3I_3III
  (JNIEnv *env, jobject obj, jintArray src, jint x, jint y, jint width,
   jint stride, jint height, jint pf, jobjectArray dstobjs,
   jintArray jDstOffsets, jintArray jDstStrides, jint subsamp, jint flags)
{
  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in encodeYUV()");
  if (tjPixelSize[pf] != sizeof(jint))
    THROW_ARG("Pixel format must be 32-bit when encoding from an integer buffer.");

  TJCompressor_encodeYUV(env, obj, src, sizeof(jint), x, y, width,
                         stride * sizeof(jint), height, pf, dstobjs,
                         jDstOffsets, jDstStrides, subsamp, flags);

bailout:
  return;
}

static void JNICALL TJCompressor_encodeYUV_12
  (JNIEnv *env, jobject obj, jarray src, jint srcElementSize, jint width,
   jint pitch, jint height, jint pf, jbyteArray dst, jint subsamp, jint flags)
{
  tjhandle handle = 0;
  jsize arraySize = 0;
  unsigned char *srcBuf = NULL, *dstBuf = NULL;

  GET_HANDLE();

  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF || width < 1 ||
      height < 1 || pitch < 0)
    THROW_ARG("Invalid argument in encodeYUV()");
  if (org_libjpegturbo_turbojpeg_TJ_NUMPF != TJ_NUMPF)
    THROW_ARG("Mismatch between Java and C API");

  arraySize = (pitch == 0) ? width * tjPixelSize[pf] * height : pitch * height;
  if ((*env)->GetArrayLength(env, src) * srcElementSize < arraySize)
    THROW_ARG("Source buffer is not large enough");
  if ((*env)->GetArrayLength(env, dst) <
      (jsize)tjBufSizeYUV(width, height, subsamp))
    THROW_ARG("Destination buffer is not large enough");

  BAILIF0(srcBuf = (*env)->GetPrimitiveArrayCritical(env, src, 0));
  BAILIF0(dstBuf = (*env)->GetPrimitiveArrayCritical(env, dst, 0));

  if (tjEncodeYUV2(handle, srcBuf, width, pitch, height, pf, dstBuf, subsamp,
                   flags) == -1) {
    SAFE_RELEASE(dst, dstBuf);
    SAFE_RELEASE(src, srcBuf);
    THROW_TJ();
  }

bailout:
  SAFE_RELEASE(dst, dstBuf);
  SAFE_RELEASE(src, srcBuf);
}

/* TurboJPEG 1.2.x: TJCompressor::encodeYUV() byte source */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_encodeYUV___3BIIII_3BII
  (JNIEnv *env, jobject obj, jbyteArray src, jint width, jint pitch,
   jint height, jint pf, jbyteArray dst, jint subsamp, jint flags)
{
  TJCompressor_encodeYUV_12(env, obj, src, 1, width, pitch, height, pf, dst,
                            subsamp, flags);
}

/* TurboJPEG 1.2.x: TJCompressor::encodeYUV() int source */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_encodeYUV___3IIIII_3BII
  (JNIEnv *env, jobject obj, jintArray src, jint width, jint stride,
   jint height, jint pf, jbyteArray dst, jint subsamp, jint flags)
{
  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in encodeYUV()");
  if (tjPixelSize[pf] != sizeof(jint))
    THROW_ARG("Pixel format must be 32-bit when encoding from an integer buffer.");

  TJCompressor_encodeYUV_12(env, obj, src, sizeof(jint), width,
                            stride * sizeof(jint), height, pf, dst, subsamp,
                            flags);

bailout:
  return;
}

/* TurboJPEG 1.2.x: TJCompressor::destroy() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJCompressor_destroy
  (JNIEnv *env, jobject obj)
{
  tjhandle handle = 0;

  GET_HANDLE();

  if (tjDestroy(handle) == -1) THROW_TJ();
  (*env)->SetLongField(env, obj, _fid, 0);

bailout:
  return;
}

/* TurboJPEG 1.2.x: TJDecompressor::init() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_init
  (JNIEnv *env, jobject obj)
{
  jclass cls;
  jfieldID fid;
  tjhandle handle;

  if ((handle = tjInitDecompress()) == NULL)
    THROW(tjGetErrorStr(), "org/libjpegturbo/turbojpeg/TJException");

  BAILIF0(cls = (*env)->GetObjectClass(env, obj));
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "handle", "J"));
  (*env)->SetLongField(env, obj, fid, (size_t)handle);

bailout:
  return;
}

/* TurboJPEG 1.2.x: TJDecompressor::getScalingFactors() */
JNIEXPORT jobjectArray JNICALL Java_org_libjpegturbo_turbojpeg_TJ_getScalingFactors
  (JNIEnv *env, jclass cls)
{
  jclass sfcls = NULL;
  jfieldID fid = 0;
  tjscalingfactor *sf = NULL;
  int n = 0, i;
  jobject sfobj = NULL;
  jobjectArray sfjava = NULL;

  if ((sf = tjGetScalingFactors(&n)) == NULL || n == 0)
    THROW_ARG(tjGetErrorStr());

  BAILIF0(sfcls = (*env)->FindClass(env,
    "org/libjpegturbo/turbojpeg/TJScalingFactor"));
  BAILIF0(sfjava = (jobjectArray)(*env)->NewObjectArray(env, n, sfcls, 0));

  for (i = 0; i < n; i++) {
    BAILIF0(sfobj = (*env)->AllocObject(env, sfcls));
    BAILIF0(fid = (*env)->GetFieldID(env, sfcls, "num", "I"));
    (*env)->SetIntField(env, sfobj, fid, sf[i].num);
    BAILIF0(fid = (*env)->GetFieldID(env, sfcls, "denom", "I"));
    (*env)->SetIntField(env, sfobj, fid, sf[i].denom);
    (*env)->SetObjectArrayElement(env, sfjava, i, sfobj);
  }

bailout:
  return sfjava;
}

/* TurboJPEG 1.2.x: TJDecompressor::decompressHeader() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decompressHeader
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize)
{
  tjhandle handle = 0;
  unsigned char *jpegBuf = NULL;
  int width = 0, height = 0, jpegSubsamp = -1, jpegColorspace = -1;

  GET_HANDLE();

  if ((*env)->GetArrayLength(env, src) < jpegSize)
    THROW_ARG("Source buffer is not large enough");

  BAILIF0(jpegBuf = (*env)->GetPrimitiveArrayCritical(env, src, 0));

  if (tjDecompressHeader3(handle, jpegBuf, (unsigned long)jpegSize, &width,
                          &height, &jpegSubsamp, &jpegColorspace) == -1) {
    SAFE_RELEASE(src, jpegBuf);
    THROW_TJ();
  }

  SAFE_RELEASE(src, jpegBuf);

  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegSubsamp", "I"));
  (*env)->SetIntField(env, obj, _fid, jpegSubsamp);
  if ((_fid = (*env)->GetFieldID(env, _cls, "jpegColorspace", "I")) == 0)
    (*env)->ExceptionClear(env);
  else
    (*env)->SetIntField(env, obj, _fid, jpegColorspace);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegWidth", "I"));
  (*env)->SetIntField(env, obj, _fid, width);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegHeight", "I"));
  (*env)->SetIntField(env, obj, _fid, height);

bailout:
  SAFE_RELEASE(src, jpegBuf);
}

static void TJDecompressor_decompress
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize, jarray dst,
   jint dstElementSize, jint x, jint y, jint width, jint pitch, jint height,
   jint pf, jint flags)
{
  tjhandle handle = 0;
  jsize arraySize = 0, actualPitch;
  unsigned char *jpegBuf = NULL, *dstBuf = NULL;

  GET_HANDLE();

  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in decompress()");
  if (org_libjpegturbo_turbojpeg_TJ_NUMPF != TJ_NUMPF)
    THROW_ARG("Mismatch between Java and C API");

  if ((*env)->GetArrayLength(env, src) < jpegSize)
    THROW_ARG("Source buffer is not large enough");
  actualPitch = (pitch == 0) ? width * tjPixelSize[pf] : pitch;
  arraySize = (y + height - 1) * actualPitch + (x + width) * tjPixelSize[pf];
  if ((*env)->GetArrayLength(env, dst) * dstElementSize < arraySize)
    THROW_ARG("Destination buffer is not large enough");

  BAILIF0(jpegBuf = (*env)->GetPrimitiveArrayCritical(env, src, 0));
  BAILIF0(dstBuf = (*env)->GetPrimitiveArrayCritical(env, dst, 0));

  if (tjDecompress2(handle, jpegBuf, (unsigned long)jpegSize,
                    &dstBuf[y * actualPitch + x * tjPixelSize[pf]], width,
                    pitch, height, pf, flags) == -1) {
    SAFE_RELEASE(dst, dstBuf);
    SAFE_RELEASE(src, jpegBuf);
    THROW_TJ();
  }

bailout:
  SAFE_RELEASE(dst, dstBuf);
  SAFE_RELEASE(src, jpegBuf);
}

/* TurboJPEG 1.3.x: TJDecompressor::decompress() byte destination */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decompress___3BI_3BIIIIIII
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize, jbyteArray dst,
   jint x, jint y, jint width, jint pitch, jint height, jint pf, jint flags)
{
  TJDecompressor_decompress(env, obj, src, jpegSize, dst, 1, x, y, width,
                            pitch, height, pf, flags);
}

/* TurboJPEG 1.2.x: TJDecompressor::decompress() byte destination */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decompress___3BI_3BIIIII
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize, jbyteArray dst,
   jint width, jint pitch, jint height, jint pf, jint flags)
{
  TJDecompressor_decompress(env, obj, src, jpegSize, dst, 1, 0, 0, width,
                            pitch, height, pf, flags);
}

/* TurboJPEG 1.3.x: TJDecompressor::decompress() int destination */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decompress___3BI_3IIIIIIII
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize, jintArray dst,
   jint x, jint y, jint width, jint stride, jint height, jint pf, jint flags)
{
  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in decompress()");
  if (tjPixelSize[pf] != sizeof(jint))
    THROW_ARG("Pixel format must be 32-bit when decompressing to an integer buffer.");

  TJDecompressor_decompress(env, obj, src, jpegSize, dst, sizeof(jint), x, y,
                            width, stride * sizeof(jint), height, pf, flags);

bailout:
  return;
}

/* TurboJPEG 1.2.x: TJDecompressor::decompress() int destination */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decompress___3BI_3IIIIII
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize, jintArray dst,
   jint width, jint stride, jint height, jint pf, jint flags)
{
  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in decompress()");
  if (tjPixelSize[pf] != sizeof(jint))
    THROW_ARG("Pixel format must be 32-bit when decompressing to an integer buffer.");

  TJDecompressor_decompress(env, obj, src, jpegSize, dst, sizeof(jint), 0, 0,
                            width, stride * sizeof(jint), height, pf, flags);

bailout:
  return;
}

/* TurboJPEG 1.4.x: TJDecompressor::decompressToYUV() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decompressToYUV___3BI_3_3B_3II_3III
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize,
   jobjectArray dstobjs, jintArray jDstOffsets, jint desiredWidth,
   jintArray jDstStrides, jint desiredHeight, jint flags)
{
  tjhandle handle = 0;
  jbyteArray jDstPlanes[3] = { NULL, NULL, NULL };
  unsigned char *jpegBuf = NULL, *dstPlanes[3];
  int *dstOffsets = NULL, *dstStrides = NULL;
  int jpegSubsamp = -1, jpegWidth = 0, jpegHeight = 0;
  int nc = 0, i, width, height, scaledWidth, scaledHeight, nsf = 0;
  tjscalingfactor *sf;

  GET_HANDLE();

  if ((*env)->GetArrayLength(env, src) < jpegSize)
    THROW_ARG("Source buffer is not large enough");
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegSubsamp", "I"));
  jpegSubsamp = (int)(*env)->GetIntField(env, obj, _fid);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegWidth", "I"));
  jpegWidth = (int)(*env)->GetIntField(env, obj, _fid);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegHeight", "I"));
  jpegHeight = (int)(*env)->GetIntField(env, obj, _fid);

  nc = (jpegSubsamp == org_libjpegturbo_turbojpeg_TJ_SAMP_GRAY ? 1 : 3);

  width = desiredWidth;
  height = desiredHeight;
  if (width == 0) width = jpegWidth;
  if (height == 0) height = jpegHeight;
  sf = tjGetScalingFactors(&nsf);
  if (!sf || nsf < 1)
    THROW_ARG(tjGetErrorStr());
  for (i = 0; i < nsf; i++) {
    scaledWidth = TJSCALED(jpegWidth, sf[i]);
    scaledHeight = TJSCALED(jpegHeight, sf[i]);
    if (scaledWidth <= width && scaledHeight <= height)
      break;
  }
  if (i >= nsf)
    THROW_ARG("Could not scale down to desired image dimensions");

#define RELEASE_ARRAYS_DECOMPRESSTOYUV() { \
  SAFE_RELEASE(src, jpegBuf); \
  for (i = 0; i < nc; i++) \
    SAFE_RELEASE(jDstPlanes[i], dstPlanes[i]); \
  SAFE_RELEASE(jDstStrides, dstStrides); \
  SAFE_RELEASE(jDstOffsets, dstOffsets); \
}

  BAILIF0(dstOffsets = (*env)->GetPrimitiveArrayCritical(env, jDstOffsets, 0));
  BAILIF0(dstStrides = (*env)->GetPrimitiveArrayCritical(env, jDstStrides, 0));
  for (i = 0; i < nc; i++) {
    int planeSize = tjPlaneSizeYUV(i, scaledWidth, dstStrides[i], scaledHeight,
                                   jpegSubsamp);
    int pw = tjPlaneWidth(i, scaledWidth, jpegSubsamp);

    if (planeSize < 0 || pw < 0) {
      RELEASE_ARRAYS_DECOMPRESSTOYUV();
      THROW_ARG(tjGetErrorStr());
    }

    if (dstOffsets[i] < 0) {
      RELEASE_ARRAYS_DECOMPRESSTOYUV();
      THROW_ARG("Invalid argument in decompressToYUV()");
    }
    if (dstStrides[i] < 0 && dstOffsets[i] - planeSize + pw < 0) {
      RELEASE_ARRAYS_DECOMPRESSTOYUV();
      THROW_ARG("Negative plane stride would cause memory to be accessed below plane boundary");
    }

    BAILIF0(jDstPlanes[i] = (*env)->GetObjectArrayElement(env, dstobjs, i));
    if ((*env)->GetArrayLength(env, jDstPlanes[i]) <
        dstOffsets[i] + planeSize) {
      RELEASE_ARRAYS_DECOMPRESSTOYUV();
      THROW_ARG("Destination plane is not large enough");
    }

    BAILIF0(dstPlanes[i] =
            (*env)->GetPrimitiveArrayCritical(env, jDstPlanes[i], 0));
    dstPlanes[i] = &dstPlanes[i][dstOffsets[i]];
  }
  BAILIF0(jpegBuf = (*env)->GetPrimitiveArrayCritical(env, src, 0));

  if (tjDecompressToYUVPlanes(handle, jpegBuf, (unsigned long)jpegSize,
                              dstPlanes, desiredWidth, dstStrides,
                              desiredHeight, flags) == -1) {
    RELEASE_ARRAYS_DECOMPRESSTOYUV();
    THROW_TJ();
  }

bailout:
  RELEASE_ARRAYS_DECOMPRESSTOYUV();
}

/* TurboJPEG 1.2.x: TJDecompressor::decompressToYUV() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decompressToYUV___3BI_3BI
  (JNIEnv *env, jobject obj, jbyteArray src, jint jpegSize, jbyteArray dst,
   jint flags)
{
  tjhandle handle = 0;
  unsigned char *jpegBuf = NULL, *dstBuf = NULL;
  int jpegSubsamp = -1, jpegWidth = 0, jpegHeight = 0;

  GET_HANDLE();

  if ((*env)->GetArrayLength(env, src) < jpegSize)
    THROW_ARG("Source buffer is not large enough");
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegSubsamp", "I"));
  jpegSubsamp = (int)(*env)->GetIntField(env, obj, _fid);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegWidth", "I"));
  jpegWidth = (int)(*env)->GetIntField(env, obj, _fid);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegHeight", "I"));
  jpegHeight = (int)(*env)->GetIntField(env, obj, _fid);
  if ((*env)->GetArrayLength(env, dst) <
      (jsize)tjBufSizeYUV(jpegWidth, jpegHeight, jpegSubsamp))
    THROW_ARG("Destination buffer is not large enough");

  BAILIF0(jpegBuf = (*env)->GetPrimitiveArrayCritical(env, src, 0));
  BAILIF0(dstBuf = (*env)->GetPrimitiveArrayCritical(env, dst, 0));

  if (tjDecompressToYUV(handle, jpegBuf, (unsigned long)jpegSize, dstBuf,
                        flags) == -1) {
    SAFE_RELEASE(dst, dstBuf);
    SAFE_RELEASE(src, jpegBuf);
    THROW_TJ();
  }

bailout:
  SAFE_RELEASE(dst, dstBuf);
  SAFE_RELEASE(src, jpegBuf);
}

static void TJDecompressor_decodeYUV
  (JNIEnv *env, jobject obj, jobjectArray srcobjs, jintArray jSrcOffsets,
   jintArray jSrcStrides, jint subsamp, jarray dst, jint dstElementSize,
   jint x, jint y, jint width, jint pitch, jint height, jint pf, jint flags)
{
  tjhandle handle = 0;
  jsize arraySize = 0, actualPitch;
  jbyteArray jSrcPlanes[3] = { NULL, NULL, NULL };
  const unsigned char *srcPlanes[3];
  unsigned char *dstBuf = NULL;
  int *srcOffsets = NULL, *srcStrides = NULL;
  int nc = (subsamp == org_libjpegturbo_turbojpeg_TJ_SAMP_GRAY ? 1 : 3), i;

  GET_HANDLE();

  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF || subsamp < 0 ||
      subsamp >= org_libjpegturbo_turbojpeg_TJ_NUMSAMP)
    THROW_ARG("Invalid argument in decodeYUV()");
  if (org_libjpegturbo_turbojpeg_TJ_NUMPF != TJ_NUMPF ||
      org_libjpegturbo_turbojpeg_TJ_NUMSAMP != TJ_NUMSAMP)
    THROW_ARG("Mismatch between Java and C API");

  if ((*env)->GetArrayLength(env, srcobjs) < nc)
    THROW_ARG("Planes array is too small for the subsampling type");
  if ((*env)->GetArrayLength(env, jSrcOffsets) < nc)
    THROW_ARG("Offsets array is too small for the subsampling type");
  if ((*env)->GetArrayLength(env, jSrcStrides) < nc)
    THROW_ARG("Strides array is too small for the subsampling type");

  actualPitch = (pitch == 0) ? width * tjPixelSize[pf] : pitch;
  arraySize = (y + height - 1) * actualPitch + (x + width) * tjPixelSize[pf];
  if ((*env)->GetArrayLength(env, dst) * dstElementSize < arraySize)
    THROW_ARG("Destination buffer is not large enough");

#define RELEASE_ARRAYS_DECODEYUV() { \
  SAFE_RELEASE(dst, dstBuf); \
  for (i = 0; i < nc; i++) \
    SAFE_RELEASE(jSrcPlanes[i], srcPlanes[i]); \
  SAFE_RELEASE(jSrcStrides, srcStrides); \
  SAFE_RELEASE(jSrcOffsets, srcOffsets); \
}

  BAILIF0(srcOffsets = (*env)->GetPrimitiveArrayCritical(env, jSrcOffsets, 0));
  BAILIF0(srcStrides = (*env)->GetPrimitiveArrayCritical(env, jSrcStrides, 0));
  for (i = 0; i < nc; i++) {
    int planeSize = tjPlaneSizeYUV(i, width, srcStrides[i], height, subsamp);
    int pw = tjPlaneWidth(i, width, subsamp);

    if (planeSize < 0 || pw < 0) {
      RELEASE_ARRAYS_DECODEYUV();
      THROW_ARG(tjGetErrorStr());
    }

    if (srcOffsets[i] < 0) {
      RELEASE_ARRAYS_DECODEYUV();
      THROW_ARG("Invalid argument in decodeYUV()");
    }
    if (srcStrides[i] < 0 && srcOffsets[i] - planeSize + pw < 0) {
      RELEASE_ARRAYS_DECODEYUV();
      THROW_ARG("Negative plane stride would cause memory to be accessed below plane boundary");
    }

    BAILIF0(jSrcPlanes[i] = (*env)->GetObjectArrayElement(env, srcobjs, i));
    if ((*env)->GetArrayLength(env, jSrcPlanes[i]) <
        srcOffsets[i] + planeSize) {
      RELEASE_ARRAYS_DECODEYUV();
      THROW_ARG("Source plane is not large enough");
    }

    BAILIF0(srcPlanes[i] =
            (*env)->GetPrimitiveArrayCritical(env, jSrcPlanes[i], 0));
    srcPlanes[i] = &srcPlanes[i][srcOffsets[i]];
  }
  BAILIF0(dstBuf = (*env)->GetPrimitiveArrayCritical(env, dst, 0));

  if (tjDecodeYUVPlanes(handle, srcPlanes, srcStrides, subsamp,
                        &dstBuf[y * actualPitch + x * tjPixelSize[pf]], width,
                        pitch, height, pf, flags) == -1) {
    RELEASE_ARRAYS_DECODEYUV();
    THROW_TJ();
  }

bailout:
  RELEASE_ARRAYS_DECODEYUV();
}

/* TurboJPEG 1.4.x: TJDecompressor::decodeYUV() byte destination */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decodeYUV___3_3B_3I_3II_3BIIIIIII
  (JNIEnv *env, jobject obj, jobjectArray srcobjs, jintArray jSrcOffsets,
   jintArray jSrcStrides, jint subsamp, jbyteArray dst, jint x, jint y,
   jint width, jint pitch, jint height, jint pf, jint flags)
{
  TJDecompressor_decodeYUV(env, obj, srcobjs, jSrcOffsets, jSrcStrides,
                           subsamp, dst, 1, x, y, width, pitch, height, pf,
                           flags);
}

/* TurboJPEG 1.4.x: TJDecompressor::decodeYUV() int destination */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_decodeYUV___3_3B_3I_3II_3IIIIIIII
  (JNIEnv *env, jobject obj, jobjectArray srcobjs, jintArray jSrcOffsets,
   jintArray jSrcStrides, jint subsamp, jintArray dst, jint x, jint y,
   jint width, jint stride, jint height, jint pf, jint flags)
{
  if (pf < 0 || pf >= org_libjpegturbo_turbojpeg_TJ_NUMPF)
    THROW_ARG("Invalid argument in decodeYUV()");
  if (tjPixelSize[pf] != sizeof(jint))
    THROW_ARG("Pixel format must be 32-bit when decoding to an integer buffer.");

  TJDecompressor_decodeYUV(env, obj, srcobjs, jSrcOffsets, jSrcStrides,
                           subsamp, dst, sizeof(jint), x, y, width,
                           stride * sizeof(jint), height, pf, flags);

bailout:
  return;
}

/* TurboJPEG 1.2.x: TJTransformer::init() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJTransformer_init
  (JNIEnv *env, jobject obj)
{
  jclass cls;
  jfieldID fid;
  tjhandle handle;

  if ((handle = tjInitTransform()) == NULL)
    THROW(tjGetErrorStr(), "org/libjpegturbo/turbojpeg/TJException");

  BAILIF0(cls = (*env)->GetObjectClass(env, obj));
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "handle", "J"));
  (*env)->SetLongField(env, obj, fid, (size_t)handle);

bailout:
  return;
}

typedef struct _JNICustomFilterParams {
  JNIEnv *env;
  jobject tobj;
  jobject cfobj;
} JNICustomFilterParams;

static int JNICustomFilter(short *coeffs, tjregion arrayRegion,
                           tjregion planeRegion, int componentIndex,
                           int transformIndex, tjtransform *transform)
{
  JNICustomFilterParams *params = (JNICustomFilterParams *)transform->data;
  JNIEnv *env = params->env;
  jobject tobj = params->tobj, cfobj = params->cfobj;
  jobject arrayRegionObj, planeRegionObj, bufobj, borobj;
  jclass cls;
  jmethodID mid;
  jfieldID fid;

  BAILIF0(bufobj = (*env)->NewDirectByteBuffer(env, coeffs,
    sizeof(short) * arrayRegion.w * arrayRegion.h));
  BAILIF0(cls = (*env)->FindClass(env, "java/nio/ByteOrder"));
  BAILIF0(mid = (*env)->GetStaticMethodID(env, cls, "nativeOrder",
                                          "()Ljava/nio/ByteOrder;"));
  BAILIF0(borobj = (*env)->CallStaticObjectMethod(env, cls, mid));
  BAILIF0(cls = (*env)->GetObjectClass(env, bufobj));
  BAILIF0(mid = (*env)->GetMethodID(env, cls, "order",
    "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;"));
  (*env)->CallObjectMethod(env, bufobj, mid, borobj);
  BAILIF0(mid = (*env)->GetMethodID(env, cls, "asShortBuffer",
                                    "()Ljava/nio/ShortBuffer;"));
  BAILIF0(bufobj = (*env)->CallObjectMethod(env, bufobj, mid));

  BAILIF0(cls = (*env)->FindClass(env, "java/awt/Rectangle"));
  BAILIF0(arrayRegionObj = (*env)->AllocObject(env, cls));
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "x", "I"));
  (*env)->SetIntField(env, arrayRegionObj, fid, arrayRegion.x);
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "y", "I"));
  (*env)->SetIntField(env, arrayRegionObj, fid, arrayRegion.y);
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "width", "I"));
  (*env)->SetIntField(env, arrayRegionObj, fid, arrayRegion.w);
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "height", "I"));
  (*env)->SetIntField(env, arrayRegionObj, fid, arrayRegion.h);

  BAILIF0(planeRegionObj = (*env)->AllocObject(env, cls));
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "x", "I"));
  (*env)->SetIntField(env, planeRegionObj, fid, planeRegion.x);
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "y", "I"));
  (*env)->SetIntField(env, planeRegionObj, fid, planeRegion.y);
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "width", "I"));
  (*env)->SetIntField(env, planeRegionObj, fid, planeRegion.w);
  BAILIF0(fid = (*env)->GetFieldID(env, cls, "height", "I"));
  (*env)->SetIntField(env, planeRegionObj, fid, planeRegion.h);

  BAILIF0(cls = (*env)->GetObjectClass(env, cfobj));
  BAILIF0(mid = (*env)->GetMethodID(env, cls, "customFilter",
    "(Ljava/nio/ShortBuffer;Ljava/awt/Rectangle;Ljava/awt/Rectangle;IILorg/libjpegturbo/turbojpeg/TJTransform;)V"));
  (*env)->CallVoidMethod(env, cfobj, mid, bufobj, arrayRegionObj,
                         planeRegionObj, componentIndex, transformIndex, tobj);

  return 0;

bailout:
  return -1;
}

/* TurboJPEG 1.2.x: TJTransformer::transform() */
JNIEXPORT jintArray JNICALL Java_org_libjpegturbo_turbojpeg_TJTransformer_transform
  (JNIEnv *env, jobject obj, jbyteArray jsrcBuf, jint jpegSize,
   jobjectArray dstobjs, jobjectArray tobjs, jint flags)
{
  tjhandle handle = 0;
  unsigned char *jpegBuf = NULL, **dstBufs = NULL;
  jsize n = 0;
  unsigned long *dstSizes = NULL;
  tjtransform *t = NULL;
  jbyteArray *jdstBufs = NULL;
  int i, jpegWidth = 0, jpegHeight = 0, jpegSubsamp;
  jintArray jdstSizes = 0;
  jint *dstSizesi = NULL;
  JNICustomFilterParams *params = NULL;

  GET_HANDLE();

  if ((*env)->GetArrayLength(env, jsrcBuf) < jpegSize)
    THROW_ARG("Source buffer is not large enough");
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegWidth", "I"));
  jpegWidth = (int)(*env)->GetIntField(env, obj, _fid);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegHeight", "I"));
  jpegHeight = (int)(*env)->GetIntField(env, obj, _fid);
  BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "jpegSubsamp", "I"));
  jpegSubsamp = (int)(*env)->GetIntField(env, obj, _fid);

  n = (*env)->GetArrayLength(env, dstobjs);
  if (n != (*env)->GetArrayLength(env, tobjs))
    THROW_ARG("Mismatch between size of transforms array and destination buffers array");

  if ((dstBufs =
       (unsigned char **)malloc(sizeof(unsigned char *) * n)) == NULL)
    THROW_MEM();
  if ((jdstBufs = (jbyteArray *)malloc(sizeof(jbyteArray) * n)) == NULL)
    THROW_MEM();
  if ((dstSizes = (unsigned long *)malloc(sizeof(unsigned long) * n)) == NULL)
    THROW_MEM();
  if ((t = (tjtransform *)malloc(sizeof(tjtransform) * n)) == NULL)
    THROW_MEM();
  if ((params = (JNICustomFilterParams *)malloc(sizeof(JNICustomFilterParams) *
                                                n)) == NULL)
    THROW_MEM();
  for (i = 0; i < n; i++) {
    dstBufs[i] = NULL;  jdstBufs[i] = NULL;  dstSizes[i] = 0;
    memset(&t[i], 0, sizeof(tjtransform));
    memset(&params[i], 0, sizeof(JNICustomFilterParams));
  }

  for (i = 0; i < n; i++) {
    jobject tobj, cfobj;

    BAILIF0(tobj = (*env)->GetObjectArrayElement(env, tobjs, i));
    BAILIF0(_cls = (*env)->GetObjectClass(env, tobj));
    BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "op", "I"));
    t[i].op = (*env)->GetIntField(env, tobj, _fid);
    BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "options", "I"));
    t[i].options = (*env)->GetIntField(env, tobj, _fid);
    BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "x", "I"));
    t[i].r.x = (*env)->GetIntField(env, tobj, _fid);
    BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "y", "I"));
    t[i].r.y = (*env)->GetIntField(env, tobj, _fid);
    BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "width", "I"));
    t[i].r.w = (*env)->GetIntField(env, tobj, _fid);
    BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "height", "I"));
    t[i].r.h = (*env)->GetIntField(env, tobj, _fid);

    BAILIF0(_fid = (*env)->GetFieldID(env, _cls, "cf",
      "Lorg/libjpegturbo/turbojpeg/TJCustomFilter;"));
    cfobj = (*env)->GetObjectField(env, tobj, _fid);
    if (cfobj) {
      params[i].env = env;
      params[i].tobj = tobj;
      params[i].cfobj = cfobj;
      t[i].customFilter = JNICustomFilter;
      t[i].data = (void *)&params[i];
    }
  }

  for (i = 0; i < n; i++) {
    int w = jpegWidth, h = jpegHeight;

    if (t[i].r.w != 0) w = t[i].r.w;
    if (t[i].r.h != 0) h = t[i].r.h;
    BAILIF0(jdstBufs[i] = (*env)->GetObjectArrayElement(env, dstobjs, i));
    if ((unsigned long)(*env)->GetArrayLength(env, jdstBufs[i]) <
        tjBufSize(w, h, jpegSubsamp))
      THROW_ARG("Destination buffer is not large enough");
  }
  BAILIF0(jpegBuf = (*env)->GetPrimitiveArrayCritical(env, jsrcBuf, 0));
  for (i = 0; i < n; i++)
    BAILIF0(dstBufs[i] =
            (*env)->GetPrimitiveArrayCritical(env, jdstBufs[i], 0));

  if (tjTransform(handle, jpegBuf, jpegSize, n, dstBufs, dstSizes, t,
                  flags | TJFLAG_NOREALLOC) == -1) {
    for (i = 0; i < n; i++)
      SAFE_RELEASE(jdstBufs[i], dstBufs[i]);
    SAFE_RELEASE(jsrcBuf, jpegBuf);
    THROW_TJ();
  }

  for (i = 0; i < n; i++)
    SAFE_RELEASE(jdstBufs[i], dstBufs[i]);
  SAFE_RELEASE(jsrcBuf, jpegBuf);

  jdstSizes = (*env)->NewIntArray(env, n);
  BAILIF0(dstSizesi = (*env)->GetIntArrayElements(env, jdstSizes, 0));
  for (i = 0; i < n; i++) dstSizesi[i] = (int)dstSizes[i];

bailout:
  if (dstSizesi) (*env)->ReleaseIntArrayElements(env, jdstSizes, dstSizesi, 0);
  if (dstBufs) {
    for (i = 0; i < n; i++) {
      if (dstBufs[i] && jdstBufs && jdstBufs[i])
        (*env)->ReleasePrimitiveArrayCritical(env, jdstBufs[i], dstBufs[i], 0);
    }
    free(dstBufs);
  }
  SAFE_RELEASE(jsrcBuf, jpegBuf);
  free(jdstBufs);
  free(dstSizes);
  free(t);
  return jdstSizes;
}

/* TurboJPEG 1.2.x: TJDecompressor::destroy() */
JNIEXPORT void JNICALL Java_org_libjpegturbo_turbojpeg_TJDecompressor_destroy
  (JNIEnv *env, jobject obj)
{
  Java_org_libjpegturbo_turbojpeg_TJCompressor_destroy(env, obj);
}
