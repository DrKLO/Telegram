/*
	FAST-EDGE
	Copyright (c) 2009 Benjamin C. Haynor

	Permission is hereby granted, free of charge, to any person
	obtaining a copy of this software and associated documentation
	files (the "Software"), to deal in the Software without
	restriction, including without limitation the rights to use,
	copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the
	Software is furnished to do so, subject to the following
	conditions:

	The above copyright notice and this permission notice shall be
	included in all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
	EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
	OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
	NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
	HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
	WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
	FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
	OTHER DEALINGS IN THE SOFTWARE.
*/

#ifndef _FASTEDGE
#define _FASTEDGE

namespace ocr{
//#define WIDTH 640			// uncomment to define width for situations where width is always known
//#define HEIGHT 480		// uncomment to define heigh for situations where height is always known

//#define CLOCK			// uncomment to show running times of image processing functions (in seconds)
//#define ABS_APPROX		// uncomment to use the absolute value approximation of sqrt(Gx ^ 2 + Gy ^2)
//#define PRINT_HISTOGRAM	// uncomment to print the histogram used to estimate the threshold
	struct image {
		int width;
		int height;
		unsigned char * pixel_data;
	};

void canny_edge_detect(struct image * img_in, struct image * img_out);
void gaussian_noise_reduce(struct image * img_in, struct image * img_out);
void calc_gradient_sobel(struct image * img_in, int g[], int dir[]);
void calc_gradient_scharr(struct image * img_in, int g_x[], int g_y[], int g[], int dir[]);
void non_max_suppression(struct image * img, int g[], int dir[]);
void estimate_threshold(struct image * img, int * high, int * low);
void hysteresis (int high, int low, struct image * img_in, struct image * img_out);
int trace (int x, int y, int low, struct image * img_in, struct image * img_out);
int range (struct image * img, int x, int y);
void dilate_1d_h(struct image * img, struct image * img_out);
void dilate_1d_v(struct image * img, struct image * img_out);
void erode_1d_h(struct image * img, struct image * img_out);
void erode_1d_v(struct image * img, struct image * img_out);
void erode(struct image * img_in, struct image * img_scratch, struct image * img_out);
void dilate(struct image * img_in, struct image * img_scratch, struct image * img_out);
void morph_open(struct image * img_in, struct image * img_scratch, struct image * img_scratch2, struct image * img_out);
void morph_close(struct image * img_in, struct image * img_scratch, struct image * img_scratch2, struct image * img_out);
}
#endif
