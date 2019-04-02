#include <jni.h>
#include <android/bitmap.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <vector>
#include <utility>
#include <string>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <libyuv.h>
#include "fast-edge.h"
#include "genann.h"

#ifndef max
#define max(a, b) (a>b ? a : b)
#define min(a, b) (a<b ? a : b)
#endif


#define TAG "ocr"
#define _LOG_WRAP(...) __VA_ARGS__
#define LOGV(...) {__android_log_print(ANDROID_LOG_VERBOSE, TAG, _LOG_WRAP(__VA_ARGS__));}
#define LOGD(...) {__android_log_print(ANDROID_LOG_DEBUG, TAG, _LOG_WRAP(__VA_ARGS__));}
#define LOGI(...) {__android_log_print(ANDROID_LOG_INFO, TAG, _LOG_WRAP(__VA_ARGS__));}
#define LOGW(...) {__android_log_print(ANDROID_LOG_WARN, TAG, _LOG_WRAP(__VA_ARGS__));}
#define LOGE(...) {__android_log_print(ANDROID_LOG_ERROR, TAG, _LOG_WRAP(__VA_ARGS__));}

namespace ocr{
	struct line{
		double theta;
		double r;
	};

	std::vector<line> detectLines(struct image* img, int threshold){
		// The size of the neighbourhood in which to search for other local maxima
		const int neighbourhoodSize = 4;

		// How many discrete values of theta shall we check?
		const int maxTheta = 180;

		// Using maxTheta, work out the step
		const double thetaStep = M_PI / maxTheta;

		int width=img->width;
		int height=img->height;
		// Calculate the maximum height the hough array needs to have
		int houghHeight = (int) (sqrt(2.0) * max(height, width)) / 2;

		// Double the height of the hough array to cope with negative r values
		int doubleHeight = 2 * houghHeight;

		// Create the hough array
		int* houghArray = new int[maxTheta*doubleHeight];
		memset(houghArray, 0, sizeof(int)*maxTheta*doubleHeight);

		// Find edge points and vote in array
		int centerX = width / 2;
		int centerY = height / 2;

		// Count how many points there are
		int numPoints = 0;

		// cache the values of sin and cos for faster processing
		double* sinCache = new double[maxTheta];
		double* cosCache = new double[maxTheta];
		for (int t = 0; t < maxTheta; t++) {
			double realTheta = t * thetaStep;
			sinCache[t] = sin(realTheta);
			cosCache[t] = cos(realTheta);
		}

		// Now find edge points and update the hough array
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				// Find non-black pixels
				if ((img->pixel_data[y*width+x] & 0x000000ff) != 0) {
					// Go through each value of theta
					for (int t = 0; t < maxTheta; t++) {

						//Work out the r values for each theta step
						int r = (int) (((x - centerX) * cosCache[t]) + ((y - centerY) * sinCache[t]));

						// this copes with negative values of r
						r += houghHeight;

						if (r < 0 || r >= doubleHeight) continue;

						// Increment the hough array
						houghArray[t*doubleHeight+r]++;

					}

					numPoints++;
				}
			}
		}

		// Initialise the vector of lines that we'll return
		std::vector<line> lines;

		// Only proceed if the hough array is not empty
		if (numPoints == 0){
			delete[] houghArray;
			delete[] sinCache;
			delete[] cosCache;
			return lines;
		}

		// Search for local peaks above threshold to draw
		for (int t = 0; t < maxTheta; t++) {
			//loop:
			for (int r = neighbourhoodSize; r < doubleHeight - neighbourhoodSize; r++) {

				// Only consider points above threshold
				if (houghArray[t*doubleHeight+r] > threshold) {

					int peak = houghArray[t*doubleHeight+r];

					// Check that this peak is indeed the local maxima
					for (int dx = -neighbourhoodSize; dx <= neighbourhoodSize; dx++) {
						for (int dy = -neighbourhoodSize; dy <= neighbourhoodSize; dy++) {
							int dt = t + dx;
							int dr = r + dy;
							if (dt < 0) dt = dt + maxTheta;
							else if (dt >= maxTheta) dt = dt - maxTheta;
							if (houghArray[dt*doubleHeight+dr] > peak) {
								// found a bigger point nearby, skip
								goto loop;
							}
						}
					}

					// calculate the true value of theta
					double theta = t * thetaStep;

					// add the line to the vector
					line l={theta, (double)r-houghHeight};
					lines.push_back(l);

				}
				loop:
				continue;
			}
		}

		delete[] houghArray;
		delete[] sinCache;
		delete[] cosCache;
		return lines;

	}

	void binarizeBitmapPart(uint32_t* inPixels, unsigned char* outPixels, size_t width, size_t height, size_t inStride, size_t outStride){
		uint32_t histogram[256]={0};
		uint32_t intensitySum=0;
		for(unsigned int y=0;y<height;y++){
			for(unsigned int x=0;x<width;x++){
				uint32_t px=inPixels[y*inStride/sizeof(uint32_t)+x];
				int l=(((px & 0xFF)+((px & 0xFF00) >> 8)+((px & 0xFF0000) >> 16))/3);
				outPixels[y*outStride+x]=(unsigned char)l;
				histogram[l]++;
				intensitySum+=l;
			}
		}
		int threshold=0;
		double best_sigma = 0.0;

		int first_class_pixel_count = 0;
		int first_class_intensity_sum = 0;

		for (int thresh = 0; thresh < 255; ++thresh) {
			first_class_pixel_count += histogram[thresh];
			first_class_intensity_sum += thresh * histogram[thresh];

			double first_class_prob = first_class_pixel_count / (double) (width*height);
			double second_class_prob = 1.0 - first_class_prob;

			double first_class_mean = first_class_intensity_sum / (double) first_class_pixel_count;
			double second_class_mean = (intensitySum - first_class_intensity_sum)
									   / (double) ((width*height) - first_class_pixel_count);

			double mean_delta = first_class_mean - second_class_mean;

			double sigma = first_class_prob * second_class_prob * mean_delta * mean_delta;

			if (sigma > best_sigma) {
				best_sigma = sigma;
				threshold = thresh;
			}
		}

		for(unsigned int y=0;y<height;y++){
			for(unsigned int x=0;x<width;x++){
				uint32_t px=inPixels[y*inStride/sizeof(uint32_t)+x];
				outPixels[y*outStride+x]=(px & 0xFF)<threshold && ((px & 0xFF00) >> 8)<threshold && ((px & 0xFF0000) >> 16)<threshold ? (unsigned char)255 : (unsigned char)0;
			}
		}
	}
}

extern "C" JNIEXPORT jintArray Java_org_telegram_messenger_MrzRecognizer_findCornerPoints(JNIEnv* env, jclass clasz, jobject bitmap){
	AndroidBitmapInfo info={0};
	if(AndroidBitmap_getInfo(env, bitmap, &info)!=ANDROID_BITMAP_RESULT_SUCCESS){
		return NULL;
	}
	if(info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888){
		return NULL;
	}
	//LOGD("Bitmap info: %d x %d, stride %d", info.width, info.height, info.stride);
	unsigned int width=info.width;
	unsigned int height=info.height;
	uint32_t* bitmapPixels;
	if(AndroidBitmap_lockPixels(env, bitmap, reinterpret_cast<void**>(&bitmapPixels))!=ANDROID_BITMAP_RESULT_SUCCESS){
		LOGE("AndroidBitmap_lockPixels failed!");
		return NULL;
	}
	struct ocr::image imgIn, imgOut;
	imgIn.width=imgOut.width=width;
	imgIn.height=imgOut.height=height;
	imgIn.pixel_data=(unsigned char*)malloc(width*height);
	imgOut.pixel_data=(unsigned char*)calloc(width*height, 1);
	for(unsigned int y=0;y<height;y++){
		for(unsigned int x=0;x<width;x++){
			uint32_t px=bitmapPixels[info.stride*y/sizeof(uint32_t)+x];
			imgIn.pixel_data[width*y+x]=(unsigned char) (((px & 0xFF)+((px & 0xFF00) >> 8)+((px & 0xFF0000) >> 16))/3);
		}
	}
	AndroidBitmap_unlockPixels(env, bitmap);

	ocr::canny_edge_detect(&imgIn, &imgOut);

	std::vector<ocr::line> lines=ocr::detectLines(&imgOut, 100);
	for(int i=0;i<width*height;i++){
		imgOut.pixel_data[i]/=2;
	}
	std::vector<std::vector<ocr::line>> parallelGroups;
	for(int i=0;i<36;i++){
		parallelGroups.emplace_back();
	}
	ocr::line* left=NULL;
	ocr::line* right=NULL;
	ocr::line* top=NULL;
	ocr::line* bottom=NULL;
	for(std::vector<ocr::line>::iterator l=lines.begin();l!=lines.end();){
		// remove lines at irrelevant angles
		if(!(l->theta>M_PI*0.4 && l->theta<M_PI*0.6) && !(l->theta<M_PI*0.1 || l->theta>M_PI*0.9)){
			l=lines.erase(l);
			continue;
		}
		// remove vertical lines close to the middle of the image
		if((l->theta<M_PI*0.1 || l->theta>M_PI*0.9) && abs((int)l->r)<height/4){
			l=lines.erase(l);
			continue;
		}
		// find the leftmost and rightmost lines
		if(l->theta<M_PI*0.1 || l->theta>M_PI*0.9){
			double rk=l->theta<0.5 ? 1.0 : -1.0;
			if(!left || left->r>l->r*rk){
				left=&*l;
			}
			if(!right || right->r<l->r*rk){
				right=&*l;
			}
		}
		// group parallel-ish lines with 5-degree increments
		parallelGroups[floor(l->theta/M_PI*36)].push_back(*l);
		++l;
	}

	// the text on the page tends to produce a lot of parallel lines - so we assume the top & bottom edges of the page
	// are topmost & bottommost lines in the largest group of horizontal lines
	std::vector<ocr::line>& largestParallelGroup=parallelGroups[0];
	for(std::vector<std::vector<ocr::line>>::iterator group=parallelGroups.begin();group!=parallelGroups.end();++group){
		if(largestParallelGroup.size()<group->size())
			largestParallelGroup=*group;
	}

	for(std::vector<ocr::line>::iterator l=largestParallelGroup.begin();l!=largestParallelGroup.end();++l){
		// If the image is horizontal, we assume it's just the data page or an ID card so we're going for the topmost line.
		// If it's vertical, it likely contains both the data page and the page adjacent to it so we're going for the line that is closest to the center of the image.
		// Nobody in their right mind is going to be taking vertical pictures of ID cards, right?
		if(width>height){
			if(!top || top->r>l->r){
				top=&*l;
			}
		}else{
			if(!top || fabs(l->r)<fabs(top->r)){
				top=&*l;
			}
		}
		if(!bottom || bottom->r<l->r){
			bottom=&*l;
		}
	}

	jintArray result=NULL;
	if(top && bottom && left && right){
		//LOGI("bottom theta %f", bottom->theta);
		if(bottom->theta>1.65 || bottom->theta<1.55){
			//LOGD("left: %f, right: %f\n", left->r, right->r);
			int points[8]={0};
			bool foundTopLeft=false, foundTopRight=false, foundBottomLeft=false, foundBottomRight=false;
			double centerX=width/2.0;
			double centerY=height/2.0;
			double ltsin=sin(left->theta);
			double ltcos=cos(left->theta);
			double rtsin=sin(right->theta);
			double rtcos=cos(right->theta);
			double ttsin=sin(top->theta);
			double ttcos=cos(top->theta);
			double btsin=sin(bottom->theta);
			double btcos=cos(bottom->theta);
			for (int y = -((int)height)/4; y < (int)height; y++) {
				int lx = (int) (((left->r - ((y - centerY) * ltsin)) / ltcos) + centerX);
				int ty = (int) (((top->r - ((lx - centerX) * ttcos)) / ttsin) + centerY);
				if(ty==y){
					points[0]=lx;
					points[1]=y;
					foundTopLeft=true;
					if(foundTopRight)
						break;
				}
				int rx = (int) (((right->r - ((y - centerY) * rtsin)) / rtcos) + centerX);
				ty = (int) (((top->r - ((rx - centerX) * ttcos)) / ttsin) + centerY);
				if(ty==y){
					points[2]=rx;
					points[3]=y;
					foundTopRight=true;
					if(foundTopLeft)
						break;
				}
			}
			for (int y = height+height/3; y>=0; y--) {
				int lx = (int) (((left->r - ((y - centerY) * ltsin)) / ltcos) + centerX);
				int by = (int) (((bottom->r - ((lx - centerX) * btcos)) / btsin) + centerY);
				if(by==y){
					points[4]=lx;
					points[5]=y;
					foundBottomLeft=true;
					if(foundBottomRight)
						break;
				}
				int rx = (int) (((right->r - ((y - centerY) * rtsin)) / rtcos) + centerX);
				by = (int) (((bottom->r - ((rx - centerX) * btcos)) / btsin) + centerY);
				if(by==y){
					points[6]=rx;
					points[7]=y;
					foundBottomRight=true;
					if(foundBottomLeft)
						break;
				}
			}
			if(foundTopLeft && foundTopRight && foundBottomLeft && foundBottomRight){
				result=env->NewIntArray(8);
				env->SetIntArrayRegion(result, 0, 8, points);
				//LOGD("Points: (%d %d) (%d %d) (%d %d) (%d %d)", points[0], points[1], points[2], points[3], points[4], points[5], points[6], points[7]);
			}
		}else{
			//LOGD("No perspective correction needed");
		}
	}

	free(imgIn.pixel_data);
	free(imgOut.pixel_data);

	return result;
}

extern "C" JNIEXPORT jobjectArray Java_org_telegram_messenger_MrzRecognizer_binarizeAndFindCharacters(JNIEnv* env, jclass clasz, jobject inBmp, jobject outBmp){
	AndroidBitmapInfo inInfo={0}, outInfo={0};
	if(AndroidBitmap_getInfo(env, inBmp, &inInfo)!=ANDROID_BITMAP_RESULT_SUCCESS || AndroidBitmap_getInfo(env, outBmp, &outInfo)!=ANDROID_BITMAP_RESULT_SUCCESS){
		LOGE("AndroidBitmap_getInfo failed");
		return NULL;
	}
	if(inInfo.width!=outInfo.width || inInfo.height!=outInfo.height || inInfo.format!=ANDROID_BITMAP_FORMAT_RGBA_8888 || outInfo.format!=ANDROID_BITMAP_FORMAT_A_8){
		LOGE("bitmap validation failed");
		return NULL;
	}
	unsigned int height=inInfo.height;
	unsigned int width=inInfo.width;
	uint32_t* inPixels;
	unsigned char* outPixels;
	if(AndroidBitmap_lockPixels(env, inBmp, reinterpret_cast<void**>(&inPixels))!=ANDROID_BITMAP_RESULT_SUCCESS){
		LOGE("AndroidBitmap_lockPixels failed");
		return NULL;
	}
	if(AndroidBitmap_lockPixels(env, outBmp, reinterpret_cast<void**>(&outPixels))!=ANDROID_BITMAP_RESULT_SUCCESS){
		AndroidBitmap_unlockPixels(env, inBmp);
		LOGE("AndroidBitmap_lockPixels failed");
		return NULL;
	}

	for(unsigned int y=0;y<height;y+=120){
		for(unsigned int x=0; x<width; x+=120){
			int partWidth=x+120<width ? 120 : (width-x);
			int partHeight=y+120<height ? 120 : (height-y);
			ocr::binarizeBitmapPart(&inPixels[(y*inInfo.stride/sizeof(uint32_t))+x], outPixels+(y*outInfo.stride)+x, partWidth, partHeight, inInfo.stride, outInfo.stride);
		}
	}

	// remove any single pixels without adjacent ones - these are usually noise
	for(unsigned int y=height/2;y<height-1;y++){
		unsigned int yOffset=y*outInfo.stride;
		unsigned int yOffsetPrev=(y-1)*outInfo.stride;
		unsigned int yOffsetNext=(y+1)*outInfo.stride;
		for(unsigned int x=1;x<width-1;x++){
			int pixelCount=0;
			if(outPixels[yOffsetPrev+x-1]!=0)
				pixelCount++;
			if(outPixels[yOffsetPrev+x]!=0)
				pixelCount++;
			if(outPixels[yOffsetPrev+x+1]!=0)
				pixelCount++;

			if(outPixels[yOffset+x-1]!=0)
				pixelCount++;
			if(outPixels[yOffset+x]!=0)
				pixelCount++;
			if(outPixels[yOffset+x+1]!=0)
				pixelCount++;

			if(outPixels[yOffsetNext+x-1]!=0)
				pixelCount++;
			if(outPixels[yOffsetNext+x]!=0)
				pixelCount++;
			if(outPixels[yOffsetNext+x+1]!=0)
				pixelCount++;

			if(pixelCount<3)
				outPixels[yOffset+x]=0;
		}
	}

	// search from the bottom up for continuous areas of mostly empty pixels
	unsigned int consecutiveEmptyRows=0;
	std::vector<std::pair<unsigned int, unsigned int>> emptyAreaYs;
	for(unsigned int y=height-1;y>=height/2;y--){
		unsigned int consecutiveEmptyPixels=0;
		unsigned int maxEmptyPixels=0;
		for(unsigned int x=0;x<width;x++){
			if(outPixels[y*outInfo.stride+x]==0){
				consecutiveEmptyPixels++;
			}else{
				maxEmptyPixels=max(maxEmptyPixels, consecutiveEmptyPixels);
				consecutiveEmptyPixels=0;
			}
		}
		maxEmptyPixels=max(maxEmptyPixels, consecutiveEmptyPixels);
		if(maxEmptyPixels>width/10*8){
			consecutiveEmptyRows++;
		}else if(consecutiveEmptyRows>0){
			emptyAreaYs.emplace_back(y, y+consecutiveEmptyRows);
			consecutiveEmptyRows=0;
		}
	}

	std::vector<jobjectArray> result;
	jclass rectClass=env->FindClass("android/graphics/Rect");
	jmethodID rectConstructor=env->GetMethodID(rectClass, "<init>", "(IIII)V");
	// using the areas found above, do the same thing but horizontally and between them in an attempt to ultimately find the bounds of the MRZ characters
	for(std::vector<std::pair<unsigned int, unsigned int>>::iterator p=emptyAreaYs.begin();p!=emptyAreaYs.end();++p){
		std::vector<std::pair<unsigned int, unsigned int>>::iterator next=std::next(p);
		if(next!=emptyAreaYs.end()){
			unsigned int lineHeight=p->first-next->second;
			// An MRZ line can't really be this thin so this probably isn't one
			if(lineHeight<10)
				continue;
			unsigned int consecutiveEmptyCols=0;
			std::vector<std::pair<unsigned int, unsigned int>> emptyAreaXs;
			for(unsigned int x=0;x<width;x++){
				unsigned int consecutiveEmptyPixels=0;
				unsigned int maxEmptyPixels=0;
				unsigned int bottomFilledPixels=0; // count these separately because we want those L's recognized correctly
				for(unsigned int y=next->second;y<p->first;y++){
					if(outPixels[y*outInfo.stride+x]==0){
						consecutiveEmptyPixels++;
					}else{
						maxEmptyPixels=max(maxEmptyPixels, consecutiveEmptyPixels);
						consecutiveEmptyPixels=0;
						if(y>p->first-3)
							bottomFilledPixels++;
					}
				}
				maxEmptyPixels=consecutiveEmptyPixels;
				if(lineHeight-maxEmptyPixels<=lineHeight/15 && bottomFilledPixels==0){
					consecutiveEmptyCols++;
				}else if(consecutiveEmptyCols>0){
					emptyAreaXs.emplace_back(x-consecutiveEmptyCols, x);
					consecutiveEmptyCols=0;
				}
			}
			if(consecutiveEmptyCols>0){
				emptyAreaXs.emplace_back(width-consecutiveEmptyCols, width);
			}
			if(emptyAreaXs.size()>30){
				bool foundLeftPadding=false;
				std::vector<jobject> rects;
				for(std::vector<std::pair<unsigned int, unsigned int>>::iterator h=emptyAreaXs.begin();h!=emptyAreaXs.end();++h){
					std::vector<std::pair<unsigned int, unsigned int>>::iterator nextH=std::next(h);
					if(!foundLeftPadding && h->second-h->first>width/35){
						foundLeftPadding=true;
					}else if(foundLeftPadding && h->second-h->first>width/30){
						if(rects.size()>=30){
							break;
						}else{
							// restart the search because now we've (hopefully) found the real padding
							rects.erase(rects.begin(), rects.end());
						}
					}
					if(nextH!=emptyAreaXs.end() && foundLeftPadding){
						unsigned int top=next->second;
						unsigned int bottom=p->first;
						// move the top and bottom edges towards each other as part of normalization
						for(unsigned int y=top;y<bottom;y++){
							bool found=false;
							for(unsigned int x=h->second; x<nextH->first; x++){
								if(outPixels[y*outInfo.stride+x]!=0){
									top=y;
									found=true;
									break;
								}
							}
							if(found)
								break;
						}
						for(unsigned int y=bottom;y>top;y--){
							bool found=false;
							for(unsigned int x=h->second; x<nextH->first; x++){
								if(outPixels[y*outInfo.stride+x]!=0){
									bottom=y;
									found=true;
									break;
								}
							}
							if(found)
								break;
						}
						if(bottom-top<lineHeight/4)
							continue;
						if(rects.size()<44){
							jobject rect=env->NewObject(rectClass, rectConstructor, h->second, top, nextH->first, bottom);
							rects.push_back(rect);
						}
					}
				}
				jobjectArray lineArray=env->NewObjectArray(static_cast<jsize>(rects.size()), rectClass, NULL);
				int i=0;
				for(std::vector<jobject>::iterator r=rects.begin();r!=rects.end();++r){
					env->SetObjectArrayElement(lineArray, i, *r);
					i++;
				}
				result.push_back(lineArray);
				if((rects.size()>=44 && result.size()==2) || (rects.size()>=30 && result.size()==3)){
					break;
				}
			}
		}
	}

	AndroidBitmap_unlockPixels(env, inBmp);
	AndroidBitmap_unlockPixels(env, outBmp);

	if(result.empty())
		return NULL;

	jobjectArray resultArray=env->NewObjectArray(static_cast<jsize>(result.size()), env->GetObjectClass(result[0]), NULL);
	int i=0;
	for(std::vector<jobjectArray>::iterator a=result.begin();a!=result.end();++a){
		env->SetObjectArrayElement(resultArray, static_cast<jsize>(result.size()-i-1), *a);
		i++;
	}
	return resultArray;
}

extern "C" JNIEXPORT jstring Java_org_telegram_messenger_MrzRecognizer_performRecognition(JNIEnv* env, jclass clasz, jobject bitmap, jint numRows, jint numCols, jobject jAssetManager){
	AAssetManager* assets=AAssetManager_fromJava(env, jAssetManager);
	AAsset* nnData=AAssetManager_open(assets, "secureid_ocr_nn.dat", AASSET_MODE_STREAMING);
	if(!nnData){
		LOGE("AAssetManager_open failed");
		return NULL;
	}
	struct genann* ann=genann_init(150, 1, 90, 37);
	AAsset_read(nnData, ann->weight, sizeof(double)*ann->total_weights);
	AAsset_close(nnData);
	std::string res;
	const char* alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890<";
	AndroidBitmapInfo info;
	unsigned char* pixels;
	AndroidBitmap_getInfo(env, bitmap, &info);
	if(AndroidBitmap_lockPixels(env, bitmap, reinterpret_cast<void**>(&pixels))!=ANDROID_BITMAP_RESULT_SUCCESS){
		LOGE("AndroidBitmap_lockPixels failed");
		genann_free(ann);
		return NULL;
	}
	double nnInput[150];
	for(int row=0;row<numRows;row++){
		for(int col=0;col<numCols;col++){
			unsigned int offX=static_cast<unsigned int>(col*10);
			unsigned int offY=static_cast<unsigned int>(row*15);
			for(unsigned int y=0;y<15;y++){
				for(unsigned int x=0;x<10;x++){
					nnInput[y*10+x]=(double)pixels[(offY+y)*info.stride+offX+x]/255.0;
				}
			}
			const double* nnOut=genann_run(ann, nnInput);
			unsigned int bestIndex=0;
			for(unsigned int i=0;i<37;i++){
				if(nnOut[i]>nnOut[bestIndex])
					bestIndex=i;
			}
			res+=alphabet[bestIndex];
		}
		if(row!=numRows-1)
			res+="\n";
	}
	genann_free(ann);
	return env->NewStringUTF(res.c_str());
}

extern "C" JNIEXPORT void Java_org_telegram_messenger_MrzRecognizer_setYuvBitmapPixels(JNIEnv* env, jclass clasz, jobject bitmap, jbyteArray jpixels){
	jbyte* _pixels=env->GetByteArrayElements(jpixels, NULL);
	uint8_t* pixels=reinterpret_cast<uint8_t*>(_pixels);

	AndroidBitmapInfo info;
	uint32_t* bpixels;
	if(AndroidBitmap_getInfo(env, bitmap, &info)==ANDROID_BITMAP_RESULT_SUCCESS){
		if(info.format==ANDROID_BITMAP_FORMAT_RGBA_8888){
			if(AndroidBitmap_lockPixels(env, bitmap, reinterpret_cast<void**>(&bpixels))==ANDROID_BITMAP_RESULT_SUCCESS){
				libyuv::NV12ToARGB(pixels, info.width, pixels+info.width*info.height, info.width, reinterpret_cast<uint8_t*>(bpixels), info.stride, info.width, info.height);
				AndroidBitmap_unlockPixels(env, bitmap);
			}
		}
	}

	env->ReleaseByteArrayElements(jpixels, _pixels, JNI_ABORT);
}