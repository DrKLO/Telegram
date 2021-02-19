/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef _RLOTTIE_H_
#define _RLOTTIE_H_

#include <future>
#include <vector>
#include <map>
#include <memory>

#if defined _WIN32 || defined __CYGWIN__
  #ifdef RLOTTIE_BUILD
    #define RLOTTIE_API __declspec(dllexport)
  #else
    #define RLOTTIE_API __declspec(dllimport)
  #endif
#else
  #ifdef RLOTTIE_BUILD
      #define RLOTTIE_API __attribute__ ((visibility ("default")))
  #else
      #define RLOTTIE_API
  #endif
#endif

class AnimationImpl;
struct LOTNode;
struct LOTLayerNode;

namespace rlottie {

/**
 *  @brief Configures rlottie model cache policy.
 *
 *  Provides Library level control to configure model cache
 *  policy. Setting it to 0 will disable
 *  the cache as well as flush all the previously cached content.
 *
 *  @param[in] cacheSize  Maximum Model Cache size.
 *
 *  @note to disable Caching configure with 0 size.
 *  @note to flush the current Cache content configure it with 0 and
 *        then reconfigure with the new size.
 *
 *  @internal
 */

struct Color {
    Color() = default;
    Color(float r, float g , float b):_r(r), _g(g), _b(b){}
    float r() const {return _r;}
    float g() const {return _g;}
    float b() const {return _b;}
private:
    float _r{0};
    float _g{0};
    float _b{0};
};

struct Size {
    Size() = default;
    Size(float w, float h):_w(w), _h(h){}
    float w() const {return _w;}
    float h() const {return _h;}
private:
    float _w{0};
    float _h{0};
};

struct Point {
    Point() = default;
    Point(float x, float y):_x(x), _y(y){}
    float x() const {return _x;}
    float y() const {return _y;}
private:
    float _x{0};
    float _y{0};
};

struct FrameInfo {
    explicit FrameInfo(uint32_t frame): _frameNo(frame){}
    uint32_t curFrame() const {return _frameNo;}
private:
    uint32_t _frameNo;
};

enum class Property {
    FillColor,     /*!< Color property of Fill object , value type is rlottie::Color */
    FillOpacity,   /*!< Opacity property of Fill object , value type is float [ 0 .. 100] */
    StrokeColor,   /*!< Color property of Stroke object , value type is rlottie::Color */
    StrokeOpacity, /*!< Opacity property of Stroke object , value type is float [ 0 .. 100] */
    StrokeWidth,   /*!< stroke width property of Stroke object , value type is float */
    TrAnchor,      /*!< Transform Anchor property of Layer and Group object , value type is rlottie::Point */
    TrPosition,    /*!< Transform Position property of Layer and Group object , value type is rlottie::Point */
    TrScale,       /*!< Transform Scale property of Layer and Group object , value type is rlottie::Size. range[0 ..100] */
    TrRotation,    /*!< Transform Rotation property of Layer and Group object , value type is float. range[0 .. 360] in degrees*/
    TrOpacity      /*!< Transform Opacity property of Layer and Group object , value type is float [ 0 .. 100] */
};

struct Color_Type{};
struct Point_Type{};
struct Size_Type{};
struct Float_Type{};
template <typename T> struct MapType;

class RLOTTIE_API Surface {
public:
    /**
     *  @brief Surface object constructor.
     *
     *  @param[in] buffer surface buffer.
     *  @param[in] width  surface width.
     *  @param[in] height  surface height.
     *  @param[in] bytesPerLine  number of bytes in a surface scanline.
     *
     *  @note Default surface format is ARGB32_Premultiplied.
     *
     *  @internal
     */
    Surface(uint32_t *buffer, size_t width, size_t height, size_t bytesPerLine);

    /**
     *  @brief Sets the Draw Area available on the Surface.
     *
     *  Lottie will use the draw region size to generate frame image
     *  and will update only the draw rgion of the surface.
     *
     *  @param[in] x      region area x position.
     *  @param[in] y      region area y position.
     *  @param[in] width  region area width.
     *  @param[in] height region area height.
     *
     *  @note Default surface format is ARGB32_Premultiplied.
     *  @note Default draw region area is [ 0 , 0, surface width , surface height]
     *
     *  @internal
     */
    void setDrawRegion(size_t x, size_t y, size_t width, size_t height);

    /**
     *  @brief Returns width of the surface.
     *
     *  @return surface width
     *
     *  @internal
     *
     */
    size_t width() const {return mWidth;}

    /**
     *  @brief Returns height of the surface.
     *
     *  @return surface height
     *
     *  @internal
     */
    size_t height() const {return mHeight;}

    /**
     *  @brief Returns number of bytes in the surface scanline.
     *
     *  @return number of bytes in scanline.
     *
     *  @internal
     */
    size_t  bytesPerLine() const {return mBytesPerLine;}

    /**
     *  @brief Returns buffer attached tp the surface.
     *
     *  @return buffer attaced to the Surface.
     *
     *  @internal
     */
    uint32_t *buffer() const {return mBuffer;}

    /**
     *  @brief Returns drawable area width of the surface.
     *
     *  @return drawable area width
     *
     *  @note Default value is width() of the surface
     *
     *  @internal
     *
     */
    size_t drawRegionWidth() const {return mDrawArea.w;}

    /**
     *  @brief Returns drawable area height of the surface.
     *
     *  @return drawable area height
     *
     *  @note Default value is height() of the surface
     *
     *  @internal
     */
    size_t drawRegionHeight() const {return mDrawArea.h;}

    /**
     *  @brief Returns drawable area's x position of the surface.
     *
     *  @return drawable area's x potition.
     *
     *  @note Default value is 0
     *
     *  @internal
     */
    size_t drawRegionPosX() const {return mDrawArea.x;}

    /**
     *  @brief Returns drawable area's y position of the surface.
     *
     *  @return drawable area's y potition.
     *
     *  @note Default value is 0
     *
     *  @internal
     */
    size_t drawRegionPosY() const {return mDrawArea.y;}

    /**
     *  @brief Default constructor.
     */
    Surface() = default;
private:
    uint32_t    *mBuffer{nullptr};
    size_t       mWidth{0};
    size_t       mHeight{0};
    size_t       mBytesPerLine{0};
    struct {
        size_t   x{0};
        size_t   y{0};
        size_t   w{0};
        size_t   h{0};
    }mDrawArea;
};

using MarkerList = std::vector<std::tuple<std::string, int , int>>;
/**
 *  @brief https://helpx.adobe.com/after-effects/using/layer-markers-composition-markers.html
 *  Markers exported form AE are used to describe a segmnet of an animation {comment/tag , startFrame, endFrame}
 *  Marker can be use to devide a resource in to separate animations by tagging the segment with comment string ,
 *  start frame and duration of that segment.
 */

using LayerInfoList = std::vector<std::tuple<std::string, int , int>>;


class RLOTTIE_API Animation {
public:

    /**
     *  @brief Constructs an animation object from file path.
     *
     *  @param[in] path Lottie resource file path
     *  @param[in] cachePolicy whether to cache or not the model data.
     *             use only when need to explicit disabl caching for a
     *             particular resource. To disable caching at library level
     *             use @see configureModelCacheSize() instead.
     *
     *  @return Animation object that can render the contents of the
     *          Lottie resource represented by file path.
     *
     *  @internal
     */
    static std::unique_ptr<Animation>
    loadFromFile(const std::string &path, std::map<int32_t, int32_t> *colorReplacement);

    /**
     *  @brief Constructs an animation object from JSON string data.
     *
     *  @param[in] jsonData The JSON string data.
     *  @param[in] key the string that will be used to cache the JSON string data.
     *  @param[in] resourcePath the path will be used to search for external resource.
     *  @param[in] cachePolicy whether to cache or not the model data.
     *             use only when need to explicit disabl caching for a
     *             particular resource. To disable caching at library level
     *             use @see configureModelCacheSize() instead.
     *
     *  @return Animation object that can render the contents of the
     *          Lottie resource represented by JSON string data.
     *
     *  @internal
     */
    static std::unique_ptr<Animation>
    loadFromData(std::string jsonData, const std::string &key, std::map<int32_t, int32_t> *colorReplacement, const std::string &resourcePath="");

    /**
     *  @brief Returns default framerate of the Lottie resource.
     *
     *  @return framerate of the Lottie resource
     *
     *  @internal
     *
     */
    double frameRate() const;

    /**
     *  @brief Returns total number of frames present in the Lottie resource.
     *
     *  @return frame count of the Lottie resource.
     *
     *  @note frame number starts with 0.
     *
     *  @internal
     */
    size_t totalFrame() const;

    /**
     *  @brief Returns default viewport size of the Lottie resource.
     *
     *  @param[out] width  default width of the viewport.
     *  @param[out] height default height of the viewport.
     *
     *  @internal
     *
     */
    void   size(size_t &width, size_t &height) const;

    /**
     *  @brief Returns total animation duration of Lottie resource in second.
     *         it uses totalFrame() and frameRate() to calculate the duration.
     *         duration = totalFrame() / frameRate().
     *
     *  @return total animation duration in second.
     *  @retval 0 if the Lottie resource has no animation.
     *
     *  @see totalFrame()
     *  @see frameRate()
     *
     *  @internal
     */
    double duration() const;

    /**
     *  @brief Returns frame number for a given position.
     *         this function helps to map the position value retuned
     *         by the animator to a frame number in side the Lottie resource.
     *         frame_number = lerp(start_frame, endframe, pos);
     *
     *  @param[in] pos normalized position value [0 ... 1]
     *
     *  @return frame numer maps to the position value [startFrame .... endFrame]
     *
     *  @internal
     */
    size_t frameAtPos(double pos);

    /**
     *  @brief Renders the content to surface synchronously.
     *         for performance use the async rendering @see render
     *
     *  @param[in] frameNo Content corresponds to the @p frameNo needs to be drawn
     *  @param[in] surface Surface in which content will be drawn
     *
     *  @internal
     */
    void              renderSync(size_t frameNo, Surface &surface, bool clear);

    /**
     *  @brief Returns root layer of the composition updated with
     *         content of the Lottie resource at frame number @p frameNo.
     *
     *  @param[in] frameNo Content corresponds to the @p frameNo needs to be extracted.
     *  @param[in] width   content viewbox width
     *  @param[in] height  content viewbox height
     *
     *  @return Root layer node.
     *
     *  @internal
     */
    const LOTLayerNode * renderTree(size_t frameNo, size_t width, size_t height) const;

    /**
     *  @brief Returns Composition Markers.
     *
     *
     *  @return returns MarkerList of the Composition.
     *
     *  @see MarkerList
     *  @internal
     */
    const MarkerList& markers() const;

    /**
     *  @brief Returns Layer information{name, inFrame, outFrame} of all the child layers  of the composition.
     *
     *
     *  @return List of Layer Information of the Composition.
     *
     *  @see LayerInfoList
     *  @internal
     */
    const LayerInfoList& layers() const;

    /**
     *  @brief Sets property value for the specified {@link KeyPath}. This {@link KeyPath} can resolve
     *  to multiple contents. In that case, the callback's value will apply to all of them.
     *
     *  Keypath should conatin object names separated by (.) and can handle globe(**) or wildchar(*).
     *
     *  @usage
     *  To change fillcolor property of fill1 object in the layer1->group1->fill1 hirarchy to RED color
     *
     *     player->setValue<rlottie::Property::FillColor>("layer1.group1.fill1", rlottie::Color(1, 0, 0);
     *
     *  if all the color property inside group1 needs to be changed to GREEN color
     *
     *     player->setValue<rlottie::Property::FillColor>("**.group1.**", rlottie::Color(0, 1, 0);
     *
     *  @internal
     */
    template<Property prop, typename AnyValue>
    void setValue(const std::string &keypath, AnyValue value)
    {
        setValue(MapType<std::integral_constant<Property, prop>>{}, prop, keypath, value);
    }

    /**
     *  @brief default destructor
     *
     *  @internal
     */
    ~Animation();

    std::map<int32_t, int32_t> *colorMap{nullptr};
    void resetCurrentFrame();
private:
    void setValue(Color_Type, Property, const std::string &, Color);
    void setValue(Float_Type, Property, const std::string &, float);
    void setValue(Size_Type, Property, const std::string &, Size);
    void setValue(Point_Type, Property, const std::string &, Point);

    void setValue(Color_Type, Property, const std::string &, std::function<Color(const FrameInfo &)> &&);
    void setValue(Float_Type, Property, const std::string &, std::function<float(const FrameInfo &)> &&);
    void setValue(Size_Type, Property, const std::string &, std::function<Size(const FrameInfo &)> &&);
    void setValue(Point_Type, Property, const std::string &, std::function<Point(const FrameInfo &)> &&);
    /**
     *  @brief default constructor
     *
     *  @internal
     */
    Animation();

    std::unique_ptr<AnimationImpl> d;
};

//Map Property to Value type
template<> struct MapType<std::integral_constant<Property, Property::FillColor>>: Color_Type{};
template<> struct MapType<std::integral_constant<Property, Property::StrokeColor>>: Color_Type{};
template<> struct MapType<std::integral_constant<Property, Property::FillOpacity>>: Float_Type{};
template<> struct MapType<std::integral_constant<Property, Property::StrokeOpacity>>: Float_Type{};
template<> struct MapType<std::integral_constant<Property, Property::StrokeWidth>>: Float_Type{};
template<> struct MapType<std::integral_constant<Property, Property::TrRotation>>: Float_Type{};
template<> struct MapType<std::integral_constant<Property, Property::TrOpacity>>: Float_Type{};
template<> struct MapType<std::integral_constant<Property, Property::TrAnchor>>: Point_Type{};
template<> struct MapType<std::integral_constant<Property, Property::TrPosition>>: Point_Type{};
template<> struct MapType<std::integral_constant<Property, Property::TrScale>>: Size_Type{};


}  // namespace lotplayer

#endif  // _RLOTTIE_H_
