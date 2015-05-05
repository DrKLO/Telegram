//thanks to https://github.com/koral--/android-gif-drawable
/*
 MIT License
 Copyright (c)
 
 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:
 
 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.
 
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 
 // Copyright (c) 2011 Google Inc. All rights reserved.
 //
 // Redistribution and use in source and binary forms, with or without
 // modification, are permitted provided that the following conditions are
 // met:
 //
 //    * Redistributions of source code must retain the above copyright
 // notice, this list of conditions and the following disclaimer.
 //    * Redistributions in binary form must reproduce the above
 // copyright notice, this list of conditions and the following disclaimer
 // in the documentation and/or other materials provided with the
 // distribution.
 //    * Neither the name of Google Inc. nor the names of its
 // contributors may be used to endorse or promote products derived from
 // this software without specific prior written permission.
 //
 // THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 // "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 // LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 // A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 // OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 // SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 // LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 // DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 // THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 // (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 // OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 
 The GIFLIB distribution is Copyright (c) 1997  Eric S. Raymond
 */

#include <jni.h>
#include <stdio.h>
#include <time.h>
#include <limits.h>
#include "gif.h"
#include "giflib/gif_lib.h"

#define D_GIF_ERR_NO_FRAMES     	1000
#define D_GIF_ERR_INVALID_SCR_DIMS 	1001
#define D_GIF_ERR_INVALID_IMG_DIMS 	1002
#define D_GIF_ERR_IMG_NOT_CONFINED 	1003

typedef struct {
	uint8_t blue;
	uint8_t green;
	uint8_t red;
	uint8_t alpha;
} argb;

typedef struct {
	unsigned int duration;
	int transpIndex;
	unsigned char disposalMethod;
} FrameInfo;

typedef struct {
	GifFileType *gifFilePtr;
	unsigned long lastFrameReaminder;
	unsigned long nextStartTime;
	int currentIndex;
	unsigned int lastDrawIndex;
	FrameInfo *infos;
	argb *backupPtr;
	int startPos;
	unsigned char *rasterBits;
	char *comment;
	unsigned short loopCount;
	int currentLoop;
	jfloat speedFactor;
} GifInfo;

static ColorMapObject *defaultCmap = NULL;

static ColorMapObject *genDefColorMap(void) {
	ColorMapObject *cmap = GifMakeMapObject(256, NULL);
	if (cmap != NULL) {
		int iColor;
		for (iColor = 0; iColor < 256; iColor++) {
			cmap->Colors[iColor].Red = (GifByteType) iColor;
			cmap->Colors[iColor].Green = (GifByteType) iColor;
			cmap->Colors[iColor].Blue = (GifByteType) iColor;
		}
	}
	return cmap;
}

jint gifOnJNILoad(JavaVM *vm, void *reserved, JNIEnv *env) {
    defaultCmap = genDefColorMap();
	if (defaultCmap == NULL) {
        return -1;
    }
    return JNI_VERSION_1_6;
}

void gifOnJNIUnload(JavaVM *vm, void *reserved) {
    GifFreeMapObject(defaultCmap);
}

static int fileReadFunc(GifFileType *gif, GifByteType *bytes, int size) {
    FILE *file = (FILE *)gif->UserData;
	return fread(bytes, 1, size, file);
}

static int fileRewindFun(GifInfo *info) {
    return fseek(info->gifFilePtr->UserData, info->startPos, SEEK_SET);
}

static unsigned long getRealTime() {
	struct timespec ts;
	const clockid_t id = CLOCK_MONOTONIC;
	if (id != (clockid_t) - 1 && clock_gettime(id, &ts) != -1) {
        return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    }
	return -1;
}

static void cleanUp(GifInfo *info) {
    if (info->backupPtr) {
        free(info->backupPtr);
        info->backupPtr = NULL;
    }
    if (info->infos) {
        free(info->infos);
        info->infos = NULL;
    }
    if (info->rasterBits) {
        free(info->rasterBits);
        info->rasterBits = NULL;
    }
    if (info->comment) {
        free(info->comment);
        info->comment = NULL;
    }
    
	GifFileType *GifFile = info->gifFilePtr;
	if (GifFile->SColorMap == defaultCmap) {
        GifFile->SColorMap = NULL;
    }
	if (GifFile->SavedImages != NULL) {
		SavedImage *sp;
		for (sp = GifFile->SavedImages; sp < GifFile->SavedImages + GifFile->ImageCount; sp++) {
			if (sp->ImageDesc.ColorMap != NULL) {
				GifFreeMapObject(sp->ImageDesc.ColorMap);
				sp->ImageDesc.ColorMap = NULL;
			}
		}
		free(GifFile->SavedImages);
		GifFile->SavedImages = NULL;
	}
	DGifCloseFile(GifFile);
	free(info);
}

static int getComment(GifByteType *Bytes, char **cmt) {
	unsigned int len = (unsigned int) Bytes[0];
	unsigned int offset = *cmt != NULL ? strlen(*cmt) : 0;
	char *ret = realloc(*cmt, (len + offset + 1) * sizeof(char));
	if (ret != NULL) {
		memcpy(ret + offset, &Bytes[1], len);
		ret[len + offset] = 0;
		*cmt = ret;
		return GIF_OK;
	}
	return GIF_ERROR;
}

static void packARGB32(argb *pixel, GifByteType alpha, GifByteType red, GifByteType green, GifByteType blue) {
	pixel->alpha = alpha;
	pixel->red = red;
	pixel->green = green;
	pixel->blue = blue;
}

static void getColorFromTable(int idx, argb *dst, const ColorMapObject *cmap) {
    int colIdx = (idx >= cmap->ColorCount) ? 0 : idx;
	GifColorType *col = &cmap->Colors[colIdx];
	packARGB32(dst, 0xFF, col->Red, col->Green, col->Blue);
}

static void eraseColor(argb *bm, int w, int h, argb color) {
	int i;
	for (i = 0; i < w * h; i++) {
        *(bm + i) = color;
    }
}

static inline bool setupBackupBmp(GifInfo *info, short transpIndex) {
	GifFileType *fGIF = info->gifFilePtr;
	info->backupPtr = calloc(fGIF->SWidth * fGIF->SHeight, sizeof(argb));
	if (!info->backupPtr) {
		info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
		return false;
	}
	argb paintingColor;
	if (transpIndex == -1) {
        getColorFromTable(fGIF->SBackGroundColor, &paintingColor, fGIF->SColorMap);
    } else {
        packARGB32(&paintingColor, 0, 0, 0, 0);
    }
	eraseColor(info->backupPtr, fGIF->SWidth, fGIF->SHeight, paintingColor);
	return true;
}

static int readExtensions(int ExtFunction, GifByteType *ExtData, GifInfo *info) {
	if (ExtData == NULL) {
        return GIF_OK;
    }
	if (ExtFunction == GRAPHICS_EXT_FUNC_CODE && ExtData[0] == 4) {
		FrameInfo *fi = &info->infos[info->gifFilePtr->ImageCount];
		fi->transpIndex = -1;
		char *b = (char*) ExtData + 1;
		short delay = ((b[2] << 8) | b[1]);
		fi->duration = delay > 1 ? delay * 10 : 100;
		fi->disposalMethod = ((b[0] >> 2) & 7);
		if (ExtData[1] & 1) {
            fi->transpIndex = 0xff & b[3];
        }
		if (fi->disposalMethod == 3 && info->backupPtr == NULL) {
			if (!setupBackupBmp(info, fi->transpIndex)) {
                return GIF_ERROR;
            }
		}
	} else if (ExtFunction == COMMENT_EXT_FUNC_CODE) {
		if (getComment(ExtData, &info->comment) == GIF_ERROR) {
			info->gifFilePtr->Error = D_GIF_ERR_NOT_ENOUGH_MEM;
			return GIF_ERROR;
		}
	} else if (ExtFunction == APPLICATION_EXT_FUNC_CODE && ExtData[0] == 11) {
		if (strncmp("NETSCAPE2.0", &ExtData[1], 11) == 0 || strncmp("ANIMEXTS1.0", &ExtData[1], 11) == 0) {
			if (DGifGetExtensionNext(info->gifFilePtr, &ExtData, &ExtFunction) == GIF_ERROR) {
                return GIF_ERROR;
            }
			if (ExtFunction == APPLICATION_EXT_FUNC_CODE && ExtData[0] == 3 && ExtData[1] == 1) {
				info->loopCount = (unsigned short) (ExtData[2] + (ExtData[3] << 8));
			}
		}
	}
	return GIF_OK;
}

static int DDGifSlurp(GifFileType *GifFile, GifInfo* info, bool shouldDecode) {
	GifRecordType RecordType;
	GifByteType *ExtData;
	int codeSize;
	int ExtFunction;
	size_t ImageSize;
	do {
		if (DGifGetRecordType(GifFile, &RecordType) == GIF_ERROR) {
            return (GIF_ERROR);
        }
		switch (RecordType) {
            case IMAGE_DESC_RECORD_TYPE:
            
			if (DGifGetImageDesc(GifFile, !shouldDecode) == GIF_ERROR) {
                return (GIF_ERROR);
            }
			int i = shouldDecode ? info->currentIndex : GifFile->ImageCount - 1;
			SavedImage *sp = &GifFile->SavedImages[i];
			ImageSize = sp->ImageDesc.Width * sp->ImageDesc.Height;
            
			if (sp->ImageDesc.Width < 1 || sp->ImageDesc.Height < 1 || ImageSize > (SIZE_MAX / sizeof(GifPixelType))) {
				GifFile->Error = D_GIF_ERR_INVALID_IMG_DIMS;
				return GIF_ERROR;
			}
			if (sp->ImageDesc.Width > GifFile->SWidth || sp->ImageDesc.Height > GifFile->SHeight) {
				GifFile->Error = D_GIF_ERR_IMG_NOT_CONFINED;
				return GIF_ERROR;
			}
			if (shouldDecode) {
				sp->RasterBits = info->rasterBits;
				if (sp->ImageDesc.Interlace) {
					int i, j;
					int InterlacedOffset[] = { 0, 4, 2, 1 };
					int InterlacedJumps[] = { 8, 8, 4, 2 };
					for (i = 0; i < 4; i++) {
                        for (j = InterlacedOffset[i]; j < sp->ImageDesc.Height; j += InterlacedJumps[i]) {
                            if (DGifGetLine(GifFile, sp->RasterBits + j * sp->ImageDesc.Width, sp->ImageDesc.Width) == GIF_ERROR) {
                                return GIF_ERROR;
                            }
                        }
                    }
				} else {
					if (DGifGetLine(GifFile, sp->RasterBits, ImageSize) == GIF_ERROR) {
                        return (GIF_ERROR);
                    }
				}
				if (info->currentIndex >= GifFile->ImageCount - 1) {
					if (info->loopCount > 0) {
                        info->currentLoop++;
                    }
					if (fileRewindFun(info) != 0) {
						info->gifFilePtr->Error = D_GIF_ERR_READ_FAILED;
						return GIF_ERROR;
					}
				}
				return GIF_OK;
			} else {
				if (DGifGetCode(GifFile, &codeSize, &ExtData) == GIF_ERROR) {
                    return (GIF_ERROR);
                }
				while (ExtData != NULL) {
					if (DGifGetCodeNext(GifFile, &ExtData) == GIF_ERROR) {
                        return (GIF_ERROR);
                    }
				}
			}
			break;
            
            case EXTENSION_RECORD_TYPE:
			if (DGifGetExtension(GifFile, &ExtFunction, &ExtData) == GIF_ERROR) {
                return (GIF_ERROR);
            }
            
			if (!shouldDecode) {
				FrameInfo *tmpInfos = realloc(info->infos, (GifFile->ImageCount + 1) * sizeof(FrameInfo));
                if (tmpInfos == NULL) {
                    return GIF_ERROR;
                }
                info->infos = tmpInfos;
				if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR) {
                    return GIF_ERROR;
                }
			}
			while (ExtData != NULL) {
				if (DGifGetExtensionNext(GifFile, &ExtData, &ExtFunction) == GIF_ERROR) {
                    return (GIF_ERROR);
                }
				if (!shouldDecode) {
					if (readExtensions(ExtFunction, ExtData, info) == GIF_ERROR) {
                        return GIF_ERROR;
                    }
				}
			}
			break;
            
            case TERMINATE_RECORD_TYPE:
			break;
            
            default:
			break;
		}
	} while (RecordType != TERMINATE_RECORD_TYPE);
	bool ok = true;
	if (shouldDecode) {
		ok = (fileRewindFun(info) == 0);
	}
	if (ok) {
        return (GIF_OK);
    } else {
		info->gifFilePtr->Error = D_GIF_ERR_READ_FAILED;
		return (GIF_ERROR);
	}
}

static void copyLine(argb *dst, const unsigned char *src, const ColorMapObject *cmap, int transparent, int width) {
	for (; width > 0; width--, src++, dst++) {
		if (*src != transparent) {
            getColorFromTable(*src, dst, cmap);
        }
	}
}

static argb *getAddr(argb *bm, int width, int left, int top) {
	return bm + top * width + left;
}

static void blitNormal(argb *bm, int width, int height, const SavedImage *frame, const ColorMapObject *cmap, int transparent) {
	const unsigned char* src = (unsigned char*) frame->RasterBits;
	argb *dst = getAddr(bm, width, frame->ImageDesc.Left, frame->ImageDesc.Top);
	GifWord copyWidth = frame->ImageDesc.Width;
	if (frame->ImageDesc.Left + copyWidth > width) {
		copyWidth = width - frame->ImageDesc.Left;
	}
    
	GifWord copyHeight = frame->ImageDesc.Height;
	if (frame->ImageDesc.Top + copyHeight > height) {
		copyHeight = height - frame->ImageDesc.Top;
	}
    
	for (; copyHeight > 0; copyHeight--) {
		copyLine(dst, src, cmap, transparent, copyWidth);
		src += frame->ImageDesc.Width;
		dst += width;
	}
}

static void fillRect(argb *bm, int bmWidth, int bmHeight, GifWord left, GifWord top, GifWord width, GifWord height, argb col) {
	uint32_t* dst = (uint32_t*) getAddr(bm, bmWidth, left, top);
	GifWord copyWidth = width;
	if (left + copyWidth > bmWidth) {
		copyWidth = bmWidth - left;
	}
    
	GifWord copyHeight = height;
	if (top + copyHeight > bmHeight) {
		copyHeight = bmHeight - top;
	}
	uint32_t* pColor = (uint32_t *) (&col);
	for (; copyHeight > 0; copyHeight--) {
		memset(dst, *pColor, copyWidth * sizeof(argb));
		dst += bmWidth;
	}
}

static void drawFrame(argb *bm, int bmWidth, int bmHeight, const SavedImage *frame, const ColorMapObject *cmap, short transpIndex) {
	if (frame->ImageDesc.ColorMap != NULL) {
		cmap = frame->ImageDesc.ColorMap;
		if (cmap->ColorCount != (1 << cmap->BitsPerPixel)) {
            cmap = defaultCmap;
        }
	}
	blitNormal(bm, bmWidth, bmHeight, frame, cmap, transpIndex);
}

static bool checkIfCover(const SavedImage *target, const SavedImage *covered) {
	if (target->ImageDesc.Left <= covered->ImageDesc.Left
        && covered->ImageDesc.Left + covered->ImageDesc.Width
        <= target->ImageDesc.Left + target->ImageDesc.Width
        && target->ImageDesc.Top <= covered->ImageDesc.Top
        && covered->ImageDesc.Top + covered->ImageDesc.Height
        <= target->ImageDesc.Top + target->ImageDesc.Height) {
		return true;
	}
	return false;
}

static inline void disposeFrameIfNeeded(argb *bm, GifInfo *info, unsigned int idx) {
	argb* backup = info->backupPtr;
	argb color;
	packARGB32(&color, 0, 0, 0, 0);
	GifFileType *fGif = info->gifFilePtr;
	SavedImage* cur = &fGif->SavedImages[idx - 1];
	SavedImage* next = &fGif->SavedImages[idx];
	bool curTrans = info->infos[idx - 1].transpIndex != -1;
	int curDisposal = info->infos[idx - 1].disposalMethod;
	bool nextTrans = info->infos[idx].transpIndex != -1;
	int nextDisposal = info->infos[idx].disposalMethod;
	argb *tmp;
	if ((curDisposal == 2 || curDisposal == 3) && (nextTrans || !checkIfCover(next, cur))) {
		switch (curDisposal) {
            case 2:
            
			fillRect(bm, fGif->SWidth, fGif->SHeight, cur->ImageDesc.Left, cur->ImageDesc.Top, cur->ImageDesc.Width, cur->ImageDesc.Height, color);
			break;
            
            case 3:
			tmp = bm;
			bm = backup;
			backup = tmp;
			break;
		}
	}
    
	if (nextDisposal == 3) {
        memcpy(backup, bm, fGif->SWidth * fGif->SHeight * sizeof(argb));
    }
}

static void reset(GifInfo *info) {
	if (fileRewindFun(info) != 0) {
        return;
    }
	info->nextStartTime = 0;
	info->currentLoop = -1;
	info->currentIndex = -1;
}

static void getBitmap(argb *bm, GifInfo *info) {
	GifFileType* fGIF = info->gifFilePtr;
    
	argb paintingColor;
	int i = info->currentIndex;
	if (DDGifSlurp(fGIF, info, true) == GIF_ERROR) {
        return;
    }
	SavedImage* cur = &fGIF->SavedImages[i];
	int transpIndex = info->infos[i].transpIndex;
	if (i == 0) {
		if (transpIndex == -1) {
            getColorFromTable(fGIF->SBackGroundColor, &paintingColor, fGIF->SColorMap);
        } else {
            packARGB32(&paintingColor, 0, 0, 0, 0);
        }
		eraseColor(bm, fGIF->SWidth, fGIF->SHeight, paintingColor);
	} else {
		disposeFrameIfNeeded(bm, info, i);
	}
	drawFrame(bm, fGIF->SWidth, fGIF->SHeight, cur, fGIF->SColorMap, transpIndex);
}

static void setMetaData(int width, int height, int ImageCount, int errorCode, JNIEnv *env, jintArray metaData) {
	jint *const ints = (*env)->GetIntArrayElements(env, metaData, 0);
	if (ints == NULL) {
        return;
    }
	ints[0] = width;
	ints[1] = height;
	ints[2] = ImageCount;
	ints[3] = errorCode;
	(*env)->ReleaseIntArrayElements(env, metaData, ints, 0);
}

static jint open(GifFileType *GifFileIn, int Error, int startPos, JNIEnv *env, jintArray metaData) {
	if (startPos < 0) {
		Error = D_GIF_ERR_NOT_READABLE;
		DGifCloseFile(GifFileIn);
	}
	if (Error != 0 || GifFileIn == NULL) {
		setMetaData(0, 0, 0, Error, env, metaData);
		return (jint) NULL;
	}
	int width = GifFileIn->SWidth, height = GifFileIn->SHeight;
	unsigned int wxh = width * height;
	if (wxh < 1 || wxh > INT_MAX) {
		DGifCloseFile(GifFileIn);
		setMetaData(width, height, 0, D_GIF_ERR_INVALID_SCR_DIMS, env, metaData);
		return (jint) NULL;
	}
	GifInfo *info = malloc(sizeof(GifInfo));
	if (info == NULL) {
		DGifCloseFile(GifFileIn);
		setMetaData(width, height, 0, D_GIF_ERR_NOT_ENOUGH_MEM, env, metaData);
		return (jint) NULL;
	}
	info->gifFilePtr = GifFileIn;
	info->startPos = startPos;
	info->currentIndex = -1;
	info->nextStartTime = 0;
	info->lastFrameReaminder = ULONG_MAX;
	info->comment = NULL;
	info->loopCount = 0;
	info->currentLoop = -1;
	info->speedFactor = 1.0;
	info->rasterBits = calloc(GifFileIn->SHeight * GifFileIn->SWidth, sizeof(GifPixelType));
	info->infos = malloc(sizeof(FrameInfo));
	info->backupPtr = NULL;
    
	if (info->rasterBits == NULL || info->infos == NULL) {
		cleanUp(info);
		setMetaData(width, height, 0, D_GIF_ERR_NOT_ENOUGH_MEM, env, metaData);
		return (jint) NULL;
	}
	info->infos->duration = 0;
	info->infos->disposalMethod = 0;
	info->infos->transpIndex = -1;
	if (GifFileIn->SColorMap == NULL || GifFileIn->SColorMap->ColorCount != (1 << GifFileIn->SColorMap->BitsPerPixel)) {
		GifFreeMapObject(GifFileIn->SColorMap);
		GifFileIn->SColorMap = defaultCmap;
	}

	DDGifSlurp(GifFileIn, info, false);

	int imgCount = GifFileIn->ImageCount;

	if (imgCount < 1) {
        Error = D_GIF_ERR_NO_FRAMES;
    }
	if (fileRewindFun(info) != 0) {
        Error = D_GIF_ERR_READ_FAILED;
    }
	if (Error != 0) {
        cleanUp(info);
    }
	setMetaData(width, height, imgCount, Error, env, metaData);
	return (jint)(Error == 0 ? info : NULL);
}

JNIEXPORT jlong JNICALL Java_org_telegram_ui_Components_GifDrawable_getAllocationByteCount(JNIEnv *env, jclass class, jobject gifInfo) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL) {
        return 0;
    }
	unsigned int pxCount = info->gifFilePtr->SWidth + info->gifFilePtr->SHeight;
	jlong sum = pxCount * sizeof(char);
	if (info->backupPtr != NULL) {
        sum += pxCount * sizeof(argb);
    }
	return sum;
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_reset(JNIEnv *env, jclass class, jobject gifInfo) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL) {
        return;
    }
	reset(info);
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_setSpeedFactor(JNIEnv *env, jclass class, jobject gifInfo, jfloat factor) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL) {
        return;
    }
	info->speedFactor = factor;
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_seekToTime(JNIEnv *env, jclass class, jobject gifInfo, jint desiredPos, jintArray jPixels) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL || jPixels == NULL) {
        return;
    }
	int imgCount = info->gifFilePtr->ImageCount;
	if (imgCount <= 1) {
        return;
    }
    
	unsigned long sum = 0;
	int i;
	for (i = 0; i < imgCount; i++) {
		unsigned long newSum = sum + info->infos[i].duration;
		if (newSum >= desiredPos) {
            break;
        }
		sum = newSum;
	}
	if (i < info->currentIndex) {
        return;
    }
    
	unsigned long lastFrameRemainder = desiredPos - sum;
	if (i == imgCount - 1 && lastFrameRemainder > info->infos[i].duration) {
        lastFrameRemainder = info->infos[i].duration;
    }
	if (i > info->currentIndex) {
		jint *const pixels = (*env)->GetIntArrayElements(env, jPixels, 0);
		if (pixels == NULL) {
            return;
        }
		while (info->currentIndex <= i) {
			info->currentIndex++;
			getBitmap((argb*) pixels, info);
		}
		(*env)->ReleaseIntArrayElements(env, jPixels, pixels, 0);
	}
	info->lastFrameReaminder = lastFrameRemainder;
    
	if (info->speedFactor == 1.0) {
        info->nextStartTime = getRealTime() + lastFrameRemainder;
	} else {
        info->nextStartTime = getRealTime() + lastFrameRemainder * info->speedFactor;
    }
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_seekToFrame(JNIEnv *env, jclass class, jobject gifInfo, jint desiredIdx, jintArray jPixels) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL|| jPixels==NULL) {
        return;
    }
	if (desiredIdx <= info->currentIndex) {
        return;
    }
    
	int imgCount = info->gifFilePtr->ImageCount;
	if (imgCount <= 1) {
        return;
    }
    
	jint *const pixels = (*env)->GetIntArrayElements(env, jPixels, 0);
	if (pixels == NULL) {
        return;
    }
    
	info->lastFrameReaminder = 0;
	if (desiredIdx >= imgCount) {
        desiredIdx = imgCount - 1;
    }
    
	while (info->currentIndex < desiredIdx) {
		info->currentIndex++;
		getBitmap((argb *) pixels, info);
	}
	(*env)->ReleaseIntArrayElements(env, jPixels, pixels, 0);
	if (info->speedFactor == 1.0) {
        info->nextStartTime = getRealTime() + info->infos[info->currentIndex].duration;
    } else {
        info->nextStartTime = getRealTime() + info->infos[info->currentIndex].duration * info->speedFactor;
    }
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_renderFrame(JNIEnv *env, jclass class, jintArray jPixels, jobject gifInfo, jintArray metaData) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL || jPixels == NULL) {
        return;
    }
	bool needRedraw = false;
	unsigned long rt = getRealTime();
    
	if (rt >= info->nextStartTime && info->currentLoop < info->loopCount) {
		if (++info->currentIndex >= info->gifFilePtr->ImageCount) {
            info->currentIndex = 0;
        }
		needRedraw = true;
	}
	jint *const rawMetaData = (*env)->GetIntArrayElements(env, metaData, 0);
	if (rawMetaData == NULL) {
        return;
    }
    
 	if (needRedraw) {
		jint *const pixels = (*env)->GetIntArrayElements(env, jPixels, 0);
		if (pixels == NULL) {
		    (*env)->ReleaseIntArrayElements(env, metaData, rawMetaData, 0);
		    return;
		}
		getBitmap((argb *)pixels, info);
		rawMetaData[3] = info->gifFilePtr->Error;
        
		(*env)->ReleaseIntArrayElements(env, jPixels, pixels, 0);
		unsigned int scaledDuration = info->infos[info->currentIndex].duration;
		if (info->speedFactor != 1.0) {
			scaledDuration /= info->speedFactor;
			if (scaledDuration<=0) {
                scaledDuration=1;
            } else if (scaledDuration > INT_MAX) {
                scaledDuration = INT_MAX;
            }
		}
		info->nextStartTime = rt + scaledDuration;
		rawMetaData[4] = scaledDuration;
	} else {
	    long delay = info->nextStartTime-rt;
	    if (delay < 0) {
            rawMetaData[4] = -1;
        } else {
            rawMetaData[4] = (int) delay;
        }
	}
	(*env)->ReleaseIntArrayElements(env, metaData, rawMetaData, 0);
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_free(JNIEnv *env, jclass class, jobject gifInfo) {
	if (gifInfo == NULL) {
        return;
    }
	GifInfo *info = (GifInfo *)gifInfo;
    FILE *file = info->gifFilePtr->UserData;
    if (file) {
        fclose(file);
    }
	info->gifFilePtr->UserData = NULL;
	cleanUp(info);
}

JNIEXPORT jstring JNICALL Java_org_telegram_ui_Components_GifDrawable_getComment(JNIEnv *env, jclass class, jobject gifInfo) {
	if (gifInfo == NULL) {
        return NULL;
    }
	GifInfo *info = (GifInfo *)gifInfo;
	return (*env)->NewStringUTF(env, info->comment);
}

JNIEXPORT jint JNICALL Java_org_telegram_ui_Components_GifDrawable_getLoopCount(JNIEnv *env, jclass class, jobject gifInfo) {
	if (gifInfo == NULL) {
        return 0;
    }
	return ((GifInfo *)gifInfo)->loopCount;
}

JNIEXPORT jint JNICALL Java_org_telegram_ui_Components_GifDrawable_getDuration(JNIEnv *env, jclass class, jobject gifInfo) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL) {
        return 0;
    }
	int i;
	unsigned long sum = 0;
	for (i = 0; i < info->gifFilePtr->ImageCount; i++) {
        sum += info->infos[i].duration;
    }
	return sum;
}

JNIEXPORT jint JNICALL Java_org_telegram_ui_Components_GifDrawable_getCurrentPosition(JNIEnv *env, jclass class, jobject gifInfo) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL) {
        return 0;
    }
	int idx = info->currentIndex;
	if (idx < 0 || info->gifFilePtr->ImageCount <= 1) {
        return 0;
    }
	int i;
	unsigned int sum = 0;
	for (i = 0; i < idx; i++) {
        sum += info->infos[i].duration;
    }
	unsigned long remainder = info->lastFrameReaminder == ULONG_MAX ? getRealTime() - info->nextStartTime : info->lastFrameReaminder;
	return (int) (sum + remainder);
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_saveRemainder(JNIEnv *env, jclass class, jobject gifInfo) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL) {
        return;
    }
	info->lastFrameReaminder = getRealTime() - info->nextStartTime;
}

JNIEXPORT void JNICALL Java_org_telegram_ui_Components_GifDrawable_restoreRemainder(JNIEnv *env, jclass class, jobject gifInfo) {
	GifInfo *info = (GifInfo *)gifInfo;
	if (info == NULL || info->lastFrameReaminder == ULONG_MAX) {
        return;
    }
	info->nextStartTime = getRealTime() + info->lastFrameReaminder;
	info->lastFrameReaminder = ULONG_MAX;
}

JNIEXPORT jint JNICALL Java_org_telegram_ui_Components_GifDrawable_openFile(JNIEnv *env, jclass class, jintArray metaData, jstring jfname) {
	if (jfname == NULL) {
		setMetaData(0, 0, 0, D_GIF_ERR_OPEN_FAILED, env, metaData);
		return (jint) NULL;
	}
    
	const char *const fname = (*env)->GetStringUTFChars(env, jfname, 0);
	FILE *file = fopen(fname, "rb");
	(*env)->ReleaseStringUTFChars(env, jfname, fname);
	if (file == NULL) {
		setMetaData(0, 0, 0, D_GIF_ERR_OPEN_FAILED, env, metaData);
		return (jint) NULL;
	}
	int Error = 0;
	GifFileType *GifFileIn = DGifOpen(file, &fileReadFunc, &Error);
	return open(GifFileIn, Error, ftell(file), env, metaData);
}
