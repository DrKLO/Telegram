Mozilla JPEG Encoder Project [![Build Status](https://ci.appveyor.com/api/projects/status/github/mozilla/mozjpeg?branch=master&svg=true)](https://ci.appveyor.com/project/kornel/mozjpeg-4ekrx)
============================

MozJPEG improves JPEG compression efficiency achieving higher visual quality and smaller file sizes at the same time. It is compatible with the JPEG standard, and the vast majority of the world's deployed JPEG decoders.

MozJPEG is a patch for [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo). **Please send pull requests to libjpeg-turbo** if the changes aren't specific to newly-added MozJPEG-only compression code. This project aims to keep differences with libjpeg-turbo minimal, so whenever possible, improvements and bug fixes should go there first.

MozJPEG is compatible with the libjpeg API and ABI. It is intended to be a drop-in replacement for libjpeg. MozJPEG is a strict superset of libjpeg-turbo's functionality. All MozJPEG's improvements can be disabled at run time, and in that case it behaves exactly like libjpeg-turbo.

MozJPEG is meant to be used as a library in graphics programs and image processing tools. We include a demo `cjpeg` command-line tool, but it's not intended for serious use. We encourage authors of graphics programs to use libjpeg's [C API](libjpeg.txt) and link with MozJPEG library instead.

## Features

* Progressive encoding with "jpegrescan" optimization. It can be applied to any JPEG file (with `jpegtran`) to losslessly reduce file size.
* Trellis quantization. When converting other formats to JPEG it maximizes quality/filesize ratio.
* Comes with new quantization table presets, e.g. tuned for high-resolution displays.
* Fully compatible with all web browsers.
* Can be seamlessly integrated into any program that uses the industry-standard libjpeg API. There's no need to write any MozJPEG-specific integration code.

## Releases

* [Latest release](https://github.com/mozilla/mozjpeg/releases/latest)
* [Overview of 3.0 features](https://calendar.perfplanet.com/2014/mozjpeg-3-0/)
* [Version 2.0 Announcement](https://blog.mozilla.org/research/2014/07/15/mozilla-advances-jpeg-encoding-with-mozjpeg-2-0/)
* [Version 1.0 Announcement](https://blog.mozilla.org/research/2014/03/05/introducing-the-mozjpeg-project/)

## Compiling

See [BUILDING](BUILDING.md). MozJPEG is built exactly the same way as libjpeg-turbo, so if you need additional help please consult [libjpeg-turbo documentation](https://libjpeg-turbo.org/).
