/* 
 * Copyright (c) 2018 Samsung Electronics Co., Ltd. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

#ifndef _RLOTTIE_H_
#define _RLOTTIE_H_

#include <future>
#include <vector>
#include <memory>
#include <map>

#ifdef _WIN32
#ifdef LOT_BUILD
#ifdef DLL_EXPORT
#define LOT_EXPORT __declspec(dllexport)
#else
#define LOT_EXPORT
#endif
#else
#define LOT_EXPORT __declspec(dllimport)
#endif
#else
#ifdef __GNUC__
#if __GNUC__ >= 4
#define LOT_EXPORT __attribute__((visibility("default")))
#else
#define LOT_EXPORT
#endif
#else
#define LOT_EXPORT
#endif
#endif

class AnimationImpl;
struct LOTNode;
struct LOTLayerNode;

namespace rlottie {

    enum class FitzModifier {
        None,
        Type12,
        Type3,
        Type4,
        Type5,
        Type6
    };

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
    Color,         /*!< Color property of Fill object , value type is rlottie::Color */
    FillOpacity,   /*!< Opacity property of Fill object , value type is float [ 0 .. 100] */
    StrokeOpacity, /*!< Opacity property of Stroke object , value type is float [ 0 .. 100] */
    StrokeWidth,   /*!< stroke with property of Stroke object , value type is float */
    TrAnchor,      /*!< Transform Anchor property of Layer and Group object , value type is rlottie::Point */
    TrPosition,    /*!< Transform Position property of Layer and Group object , value type is rlottie::Point */
    TrScale,       /*!< Transform Scale property of Layer and Group object , value type is rlottie::Size. range[0 ..100] */
    TrRotation,    /*!< Transform Scale property of Layer and Group object , value type is float. range[0 .. 360] in degrees*/
    TrOpacity      /*!< Transform Opacity property of Layer and Group object , value type is float [ 0 .. 100] */
};

struct Color_Type{};
struct Point_Type{};
struct Size_Type{};
struct Float_Type{};
template <typename T> struct MapType;

class LOT_EXPORT Surface {
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

using LayerInfoList = std::vector<std::tuple<std::string, int , int>>;

class LOT_EXPORT Animation {
public:

    /**
     *  @brief Constructs an animation object from file path.
     *
     *  @param[in] path Lottie resource file path
     *
     *  @return Animation object that can render the contents of the
     *          Lottie resource represented by file path.
     *
     *  @internal
     */
    static std::unique_ptr<Animation>
    loadFromFile(const std::string &path, std::map<int32_t, int32_t> *colorReplacement, FitzModifier fitzModifier);

    /**
     *  @brief Constructs an animation object from JSON string data.
     *
     *  @param[in] jsonData The JSON string data.
     *  @param[in] key the string that will be used to cache the JSON string data.
     *  @param[in] resourcePath the path will be used to search for external resource.
     *
     *  @return Animation object that can render the contents of the
     *          Lottie resource represented by JSON string data.
     *
     *  @internal
     */
    static std::unique_ptr<Animation>
    loadFromData(std::string jsonData, const std::string &key, std::map<int32_t, int32_t> *colorReplacement, FitzModifier fitzModifier = FitzModifier::None, const std::string &resourcePath="");

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
template<> struct MapType<std::integral_constant<Property, Property::Color>>: Color_Type{};
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
