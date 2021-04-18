/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

//#define DEBUG_PARSER

// This parser implements JSON token-by-token parsing with an API that is
// more direct; we don't have to create  handler object and
// callbacks. Instead, we retrieve values from the JSON stream by calling
// GetInt(), GetDouble(), GetString() and GetBool(), traverse into structures
// by calling EnterObject() and EnterArray(), and skip over unwanted data by
// calling SkipValue(). As we know the lottie file structure this way will be
// the efficient way of parsing the file.
//
// If you aren't sure of what's next in the JSON data, you can use PeekType()
// and PeekValue() to look ahead to the next object before reading it.
//
// If you call the wrong retrieval method--e.g. GetInt when the next JSON token
// is not an int, EnterObject or EnterArray when there isn't actually an object
// or array to read--the stream parsing will end immediately and no more data
// will be delivered.
//
// After calling EnterObject, you retrieve keys via NextObjectKey() and values
// via the normal getters. When NextObjectKey() returns null, you have exited
// the object, or you can call SkipObject() to skip to the end of the object
// immediately. If you fetch the entire object (i.e. NextObjectKey() returned
// null), you should not call SkipObject().
//
// After calling EnterArray(), you must alternate between calling
// NextArrayValue() to see if the array has more data, and then retrieving
// values via the normal getters. You can call SkipArray() to skip to the end of
// the array immediately. If you fetch the entire array (i.e. NextArrayValue()
// returned null), you should not call SkipArray().
//
// This parser uses in-situ strings, so the JSON buffer will be altered during
// the parse.

#include <array>

#include "lottiemodel.h"
#include "rapidjson/document.h"

RAPIDJSON_DIAG_PUSH
#ifdef __GNUC__
RAPIDJSON_DIAG_OFF(effc++)
#endif

using namespace rapidjson;

using namespace rlottie::internal;

class LookaheadParserHandler {
public:
    bool Null()
    {
        st_ = kHasNull;
        v_.SetNull();
        return true;
    }
    bool Bool(bool b)
    {
        st_ = kHasBool;
        v_.SetBool(b);
        return true;
    }
    bool Int(int i)
    {
        st_ = kHasNumber;
        v_.SetInt(i);
        return true;
    }
    bool Uint(unsigned u)
    {
        st_ = kHasNumber;
        v_.SetUint(u);
        return true;
    }
    bool Int64(int64_t i)
    {
        st_ = kHasNumber;
        v_.SetInt64(i);
        return true;
    }
    bool Uint64(int64_t u)
    {
        st_ = kHasNumber;
        v_.SetUint64(u);
        return true;
    }
    bool Double(double d)
    {
        st_ = kHasNumber;
        v_.SetDouble(d);
        return true;
    }
    bool RawNumber(const char *, SizeType, bool) { return false; }
    bool String(const char *str, SizeType length, bool)
    {
        st_ = kHasString;
        v_.SetString(str, length);
        return true;
    }
    bool StartObject()
    {
        st_ = kEnteringObject;
        return true;
    }
    bool Key(const char *str, SizeType length, bool)
    {
        st_ = kHasKey;
        v_.SetString(str, length);
        return true;
    }
    bool EndObject(SizeType)
    {
        st_ = kExitingObject;
        return true;
    }
    bool StartArray()
    {
        st_ = kEnteringArray;
        return true;
    }
    bool EndArray(SizeType)
    {
        st_ = kExitingArray;
        return true;
    }

protected:
    explicit LookaheadParserHandler(char *str);

protected:
    enum LookaheadParsingState {
        kInit,
        kError,
        kHasNull,
        kHasBool,
        kHasNumber,
        kHasString,
        kHasKey,
        kEnteringObject,
        kExitingObject,
        kEnteringArray,
        kExitingArray
    };

    Value                 v_;
    LookaheadParsingState st_;
    Reader                r_;
    InsituStringStream    ss_;

    static const int parseFlags = kParseDefaultFlags | kParseInsituFlag;
};

class LottieParserImpl : public LookaheadParserHandler {
public:
    LottieParserImpl(char *str, std::string dir_path, std::map<int32_t, int32_t> *colorReplacement)
        : LookaheadParserHandler(str),
          colorMap(colorReplacement),
          mDirPath(std::move(dir_path))
    {
    }
    bool VerifyType();
    bool ParseNext();

public:
    VArenaAlloc &allocator() { return compRef->mArenaAlloc; }
    bool         EnterObject();
    bool         EnterArray();
    const char * NextObjectKey();
    bool         NextArrayValue();
    int          GetInt();
    double       GetDouble();
    const char * GetString();
    bool         GetBool();
    void         GetNull();

    void   SkipObject();
    void   SkipArray();
    void   SkipValue();
    Value *PeekValue();
    int    PeekType() const;
    bool   IsValid() { return st_ != kError; }

    void                  Skip(const char *key);
    model::BlendMode      getBlendMode();
    CapStyle              getLineCap();
    JoinStyle             getLineJoin();
    FillRule              getFillRule();
    model::Trim::TrimType getTrimType();
    model::MatteType      getMatteType();
    model::Layer::Type    getLayerType();

    std::shared_ptr<model::Composition> composition() const
    {
        return mComposition;
    }
    void             parseComposition();
    void             parseMarkers();
    void             parseMarker();
    void             parseAssets(model::Composition *comp);
    model::Asset *   parseAsset();
    void             parseLayers(model::Composition *comp);
    model::Layer *   parseLayer();
    void             parseMaskProperty(model::Layer *layer);
    void             parseShapesAttr(model::Layer *layer);
    void             parseObject(model::Group *parent);
    model::Mask *    parseMaskObject();
    model::Object *  parseObjectTypeAttr();
    model::Object *  parseGroupObject();
    model::Rect *    parseRectObject();
    model::RoundedCorner *    parseRoundedCorner();
    void updateRoundedCorner(model::Group *parent, model::RoundedCorner *rc);

    model::Ellipse * parseEllipseObject();
    model::Path *    parseShapeObject();
    model::Polystar *parsePolystarObject();

    model::Transform *     parseTransformObject(bool ddd = false);
    model::Fill *          parseFillObject();
    model::GradientFill *  parseGFillObject();
    model::Stroke *        parseStrokeObject();
    model::GradientStroke *parseGStrokeObject();
    model::Trim *          parseTrimObject();
    model::Repeater *      parseReapeaterObject();

    void parseGradientProperty(model::Gradient *gradient, const char *key);

    VPointF parseInperpolatorPoint();

    void getValue(VPointF &pt);
    void getValue(float &fval);
    void getValue(model::Color &color);
    void getValue(int &ival);
    void getValue(model::PathData &shape);
    void getValue(model::Gradient::Data &gradient);
    void getValue(std::vector<VPointF> &v);
    void getValue(model::Repeater::Transform &);

    template <typename T, typename Tag>
    bool parseKeyFrameValue(const char *, model::Value<T, Tag> &)
    {
        return false;
    }

    template <typename T>
    bool parseKeyFrameValue(const char *                      key,
                            model::Value<T, model::Position> &value);
    template <typename T, typename Tag>
    void parseKeyFrame(model::KeyFrames<T, Tag> &obj);
    template <typename T>
    void parseProperty(model::Property<T> &obj);
    template <typename T, typename Tag>
    void parsePropertyHelper(model::Property<T, Tag> &obj);

    void parseShapeProperty(model::Property<model::PathData> &obj);
    void parseDashProperty(model::Dash &dash);

    VInterpolator *interpolator(VPointF, VPointF, std::string);

    model::Color toColor(const char *str);

    void resolveLayerRefs();
    void parsePathInfo();

private:
    std::map<int32_t, int32_t> *colorMap;
    struct {
        std::vector<VPointF> mInPoint;  /* "i" */
        std::vector<VPointF> mOutPoint; /* "o" */
        std::vector<VPointF> mVertices; /* "v" */
        std::vector<VPointF> mResult;
        bool                 mClosed{false};

        void convert()
        {
            // shape data could be empty.
            if (mInPoint.empty() || mOutPoint.empty() || mVertices.empty()) {
                mResult.clear();
                return;
            }

            /*
             * Convert the AE shape format to
             * list of bazier curves
             * The final structure will be Move +size*Cubic + Cubic (if the path
             * is closed one)
             */
            if (mInPoint.size() != mOutPoint.size() ||
                mInPoint.size() != mVertices.size()) {
                mResult.clear();
            } else {
                auto size = mVertices.size();
                mResult.push_back(mVertices[0]);
                for (size_t i = 1; i < size; i++) {
                    mResult.push_back(
                        mVertices[i - 1] +
                        mOutPoint[i - 1]);  // CP1 = start + outTangent
                    mResult.push_back(mVertices[i] +
                                      mInPoint[i]);   // CP2 = end + inTangent
                    mResult.push_back(mVertices[i]);  // end point
                }

                if (mClosed) {
                    mResult.push_back(
                        mVertices[size - 1] +
                        mOutPoint[size - 1]);  // CP1 = start + outTangent
                    mResult.push_back(mVertices[0] +
                                      mInPoint[0]);   // CP2 = end + inTangent
                    mResult.push_back(mVertices[0]);  // end point
                }
            }
        }
        void reset()
        {
            mInPoint.clear();
            mOutPoint.clear();
            mVertices.clear();
            mResult.clear();
            mClosed = false;
        }
        void updatePath(VPath &out)
        {
            if (mResult.empty()) return;

            auto size = mResult.size();
            auto points = mResult.data();
            /* reserve exact memory requirement at once
             * ptSize = size + 1(size + close)
             * elmSize = size/3 cubic + 1 move + 1 close
             */
            out.reserve(size + 1, size / 3 + 2);
            out.moveTo(points[0]);
            for (size_t i = 1; i < size; i += 3) {
                out.cubicTo(points[i], points[i + 1], points[i + 2]);
            }
            if (mClosed) out.close();
        }
    } mPathInfo;

protected:
    std::unordered_map<std::string, VInterpolator *> mInterpolatorCache;
    std::shared_ptr<model::Composition>              mComposition;
    model::Composition *                             compRef{nullptr};
    model::Layer *                                   curLayerRef{nullptr};
    std::vector<model::Layer *>                      mLayersToUpdate;
    std::string                                      mDirPath;
    void                                             SkipOut(int depth);
};

LookaheadParserHandler::LookaheadParserHandler(char *str)
    : v_(), st_(kInit), ss_(str)
{
    r_.IterativeParseInit();
}

bool LottieParserImpl::VerifyType()
{
    /* Verify the media type is lottie json.
       Could add more strict check. */
    return ParseNext();
}

bool LottieParserImpl::ParseNext()
{
    if (r_.HasParseError()) {
        st_ = kError;
        return false;
    }

    if (!r_.IterativeParseNext<parseFlags>(ss_, *this)) {
        vCritical << "Lottie file parsing error";
        st_ = kError;
        return false;
    }
    return true;
}

bool LottieParserImpl::EnterObject()
{
    if (st_ != kEnteringObject) {
        st_ = kError;
        RAPIDJSON_ASSERT(false);
        return false;
    }

    ParseNext();
    return true;
}

bool LottieParserImpl::EnterArray()
{
    if (st_ != kEnteringArray) {
        st_ = kError;
        RAPIDJSON_ASSERT(false);
        return false;
    }

    ParseNext();
    return true;
}

const char *LottieParserImpl::NextObjectKey()
{
    if (st_ == kHasKey) {
        const char *result = v_.GetString();
        ParseNext();
        return result;
    }

    /* SPECIAL CASE
     * The parser works with a prdefined rule that it will be only
     * while (NextObjectKey()) for each object but in case of our nested group
     * object we can call multiple time NextObjectKey() while exiting the object
     * so ignore those and don't put parser in the error state.
     * */
    if (st_ == kExitingArray || st_ == kEnteringObject) {
        // #ifdef DEBUG_PARSER
        //         vDebug<<"Object: Exiting nested loop";
        // #endif
        return nullptr;
    }

    if (st_ != kExitingObject) {
        RAPIDJSON_ASSERT(false);
        st_ = kError;
        return nullptr;
    }

    ParseNext();
    return nullptr;
}

bool LottieParserImpl::NextArrayValue()
{
    if (st_ == kExitingArray) {
        ParseNext();
        return false;
    }

    /* SPECIAL CASE
     * same as  NextObjectKey()
     */
    if (st_ == kExitingObject) {
        return false;
    }

    if (st_ == kError || st_ == kHasKey) {
        RAPIDJSON_ASSERT(false);
        st_ = kError;
        return false;
    }

    return true;
}

int LottieParserImpl::GetInt()
{
    if (st_ != kHasNumber || !v_.IsInt()) {
        st_ = kError;
        RAPIDJSON_ASSERT(false);
        return 0;
    }

    int result = v_.GetInt();
    ParseNext();
    return result;
}

double LottieParserImpl::GetDouble()
{
    if (st_ != kHasNumber) {
        st_ = kError;
        RAPIDJSON_ASSERT(false);
        return 0.;
    }

    double result = v_.GetDouble();
    ParseNext();
    return result;
}

bool LottieParserImpl::GetBool()
{
    if (st_ != kHasBool) {
        st_ = kError;
        RAPIDJSON_ASSERT(false);
        return false;
    }

    bool result = v_.GetBool();
    ParseNext();
    return result;
}

void LottieParserImpl::GetNull()
{
    if (st_ != kHasNull) {
        st_ = kError;
        return;
    }

    ParseNext();
}

const char *LottieParserImpl::GetString()
{
    if (st_ != kHasString) {
        st_ = kError;
        RAPIDJSON_ASSERT(false);
        return nullptr;
    }

    const char *result = v_.GetString();
    ParseNext();
    return result;
}

void LottieParserImpl::SkipOut(int depth)
{
    do {
        if (st_ == kEnteringArray || st_ == kEnteringObject) {
            ++depth;
        } else if (st_ == kExitingArray || st_ == kExitingObject) {
            --depth;
        } else if (st_ == kError) {
            RAPIDJSON_ASSERT(false);
            return;
        }

        ParseNext();
    } while (depth > 0);
}

void LottieParserImpl::SkipValue()
{
    SkipOut(0);
}

void LottieParserImpl::SkipArray()
{
    SkipOut(1);
}

void LottieParserImpl::SkipObject()
{
    SkipOut(1);
}

Value *LottieParserImpl::PeekValue()
{
    if (st_ >= kHasNull && st_ <= kHasKey) {
        return &v_;
    }

    return nullptr;
}

// returns a rapidjson::Type, or -1 for no value (at end of
// object/array)
int LottieParserImpl::PeekType() const
{
    if (st_ >= kHasNull && st_ <= kHasKey) {
        return v_.GetType();
    }

    if (st_ == kEnteringArray) {
        return kArrayType;
    }

    if (st_ == kEnteringObject) {
        return kObjectType;
    }

    return -1;
}

void LottieParserImpl::Skip(const char * /*key*/)
{
    if (PeekType() == kArrayType) {
        EnterArray();
        SkipArray();
    } else if (PeekType() == kObjectType) {
        EnterObject();
        SkipObject();
    } else {
        SkipValue();
    }
}

model::BlendMode LottieParserImpl::getBlendMode()
{
    RAPIDJSON_ASSERT(PeekType() == kNumberType);
    auto mode = model::BlendMode::Normal;

    switch (GetInt()) {
    case 1:
        mode = model::BlendMode::Multiply;
        break;
    case 2:
        mode = model::BlendMode::Screen;
        break;
    case 3:
        mode = model::BlendMode::OverLay;
        break;
    default:
        break;
    }
    return mode;
}

void LottieParserImpl::resolveLayerRefs()
{
    for (const auto &layer : mLayersToUpdate) {
        auto search = compRef->mAssets.find(layer->extra()->mPreCompRefId);
        if (search != compRef->mAssets.end()) {
            if (layer->mLayerType == model::Layer::Type::Image) {
                layer->extra()->mAsset = search->second;
            } else if (layer->mLayerType == model::Layer::Type::Precomp) {
                layer->mChildren = search->second->mLayers;
                layer->setStatic(layer->isStatic() &&
                                 search->second->isStatic());
            }
        }
    }
}

void LottieParserImpl::parseComposition()
{
    RAPIDJSON_ASSERT(PeekType() == kObjectType);
    EnterObject();
    std::shared_ptr<model::Composition> sharedComposition =
        std::make_shared<model::Composition>();
    model::Composition *comp = sharedComposition.get();
    compRef = comp;
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "v")) {
            RAPIDJSON_ASSERT(PeekType() == kStringType);
            comp->mVersion = std::string(GetString());
        } else if (0 == strcmp(key, "w")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            comp->mSize.setWidth(GetInt());
        } else if (0 == strcmp(key, "h")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            comp->mSize.setHeight(GetInt());
        } else if (0 == strcmp(key, "ip")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            comp->mStartFrame = GetDouble();
        } else if (0 == strcmp(key, "op")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            comp->mEndFrame = GetDouble();
        } else if (0 == strcmp(key, "fr")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            comp->mFrameRate = GetDouble();
        } else if (0 == strcmp(key, "assets")) {
            parseAssets(comp);
        } else if (0 == strcmp(key, "layers")) {
            parseLayers(comp);
        } else if (0 == strcmp(key, "markers")) {
            parseMarkers();
        } else {
#ifdef DEBUG_PARSER
            vWarning << "Composition Attribute Skipped : " << key;
#endif
            Skip(key);
        }
    }

    if (comp->mVersion.empty() || !comp->mRootLayer) {
        // don't have a valid bodymovin header
        return;
    }
    if (!IsValid()) {
        return;
    }

    resolveLayerRefs();
    comp->setStatic(comp->mRootLayer->isStatic());
    comp->mRootLayer->mInFrame = comp->mStartFrame;
    comp->mRootLayer->mOutFrame = comp->mEndFrame;

    mComposition = sharedComposition;
}

void LottieParserImpl::parseMarker()
{
    RAPIDJSON_ASSERT(PeekType() == kObjectType);
    EnterObject();
    std::string comment;
    int         timeframe{0};
    int         duration{0};
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "cm")) {
            RAPIDJSON_ASSERT(PeekType() == kStringType);
            comment = std::string(GetString());
        } else if (0 == strcmp(key, "tm")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            timeframe = GetDouble();
        } else if (0 == strcmp(key, "dr")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            duration = GetDouble();

        } else {
#ifdef DEBUG_PARSER
            vWarning << "Marker Attribute Skipped : " << key;
#endif
            Skip(key);
        }
    }
    compRef->mMarkers.emplace_back(std::move(comment), timeframe,
                                   timeframe + duration);
}

void LottieParserImpl::parseMarkers()
{
    RAPIDJSON_ASSERT(PeekType() == kArrayType);
    EnterArray();
    while (NextArrayValue()) {
        parseMarker();
    }
    // update the precomp layers with the actual layer object
}

void LottieParserImpl::parseAssets(model::Composition *composition)
{
    RAPIDJSON_ASSERT(PeekType() == kArrayType);
    EnterArray();
    while (NextArrayValue()) {
        auto asset = parseAsset();
        composition->mAssets[asset->mRefId] = asset;
    }
    // update the precomp layers with the actual layer object
}

static constexpr const unsigned char B64index[256] = {
    0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
    0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
    0,  0,  0,  0,  0,  0,  0,  62, 63, 62, 62, 63, 52, 53, 54, 55, 56, 57,
    58, 59, 60, 61, 0,  0,  0,  0,  0,  0,  0,  0,  1,  2,  3,  4,  5,  6,
    7,  8,  9,  10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
    25, 0,  0,  0,  0,  63, 0,  26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
    37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51};

std::string b64decode(const char *data, const size_t len)
{
    auto         p = reinterpret_cast<const unsigned char *>(data);
    int          pad = len > 0 && (len % 4 || p[len - 1] == '=');
    const size_t L = ((len + 3) / 4 - pad) * 4;
    std::string  str(L / 4 * 3 + pad, '\0');

    for (size_t i = 0, j = 0; i < L; i += 4) {
        int n = B64index[p[i]] << 18 | B64index[p[i + 1]] << 12 |
                B64index[p[i + 2]] << 6 | B64index[p[i + 3]];
        str[j++] = n >> 16;
        str[j++] = n >> 8 & 0xFF;
        str[j++] = n & 0xFF;
    }
    if (pad) {
        int n = B64index[p[L]] << 18 | B64index[p[L + 1]] << 12;
        str[str.size() - 1] = n >> 16;

        if (len > L + 2 && p[L + 2] != '=') {
            n |= B64index[p[L + 2]] << 6;
            str.push_back(n >> 8 & 0xFF);
        }
    }
    return str;
}

static std::string convertFromBase64(const std::string &str)
{
    // usual header look like "data:image/png;base64,"
    // so need to skip till ','.
    size_t startIndex = str.find(",", 0);
    startIndex += 1;  // skip ","
    size_t length = str.length() - startIndex;

    const char *b64Data = str.c_str() + startIndex;

    return b64decode(b64Data, length);
}

/*
 *  std::to_string() function is missing in VS2017
 *  so this is workaround for windows build
 */
#include <sstream>
template <class T>
static std::string toString(const T &value)
{
    std::ostringstream os;
    os << value;
    return os.str();
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/layers/shape.json
 *
 */
model::Asset *LottieParserImpl::parseAsset()
{
    RAPIDJSON_ASSERT(PeekType() == kObjectType);

    auto        asset = allocator().make<model::Asset>();
    std::string filename;
    std::string relativePath;
    bool        embededResource = false;
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "w")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            asset->mWidth = GetInt();
        } else if (0 == strcmp(key, "h")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            asset->mHeight = GetInt();
        } else if (0 == strcmp(key, "p")) { /* image name */
            asset->mAssetType = model::Asset::Type::Image;
            RAPIDJSON_ASSERT(PeekType() == kStringType);
            filename = std::string(GetString());
        } else if (0 == strcmp(key, "u")) { /* relative image path */
            RAPIDJSON_ASSERT(PeekType() == kStringType);
            relativePath = std::string(GetString());
        } else if (0 == strcmp(key, "e")) { /* relative image path */
            embededResource = GetInt();
        } else if (0 == strcmp(key, "id")) { /* reference id*/
            if (PeekType() == kStringType) {
                asset->mRefId = std::string(GetString());
            } else {
                RAPIDJSON_ASSERT(PeekType() == kNumberType);
                asset->mRefId = toString(GetInt());
            }
        } else if (0 == strcmp(key, "layers")) {
            asset->mAssetType = model::Asset::Type::Precomp;
            RAPIDJSON_ASSERT(PeekType() == kArrayType);
            EnterArray();
            bool staticFlag = true;
            while (NextArrayValue()) {
                auto layer = parseLayer();
                if (layer) {
                    staticFlag = staticFlag && layer->isStatic();
                    asset->mLayers.push_back(layer);
                }
            }
            asset->setStatic(staticFlag);
        } else {
#ifdef DEBUG_PARSER
            vWarning << "Asset Attribute Skipped : " << key;
#endif
            Skip(key);
        }
    }

    if (asset->mAssetType == model::Asset::Type::Image) {
        if (embededResource) {
            // embeder resource should start with "data:"
            if (filename.compare(0, 5, "data:") == 0) {
                asset->loadImageData(convertFromBase64(filename));
            }
        } else {
            asset->loadImagePath(mDirPath + relativePath + filename);
        }
    }

    return asset;
}

void LottieParserImpl::parseLayers(model::Composition *comp)
{
    comp->mRootLayer = allocator().make<model::Layer>();
    comp->mRootLayer->mLayerType = model::Layer::Type::Precomp;
    comp->mRootLayer->setName("__");
    bool staticFlag = true;
    RAPIDJSON_ASSERT(PeekType() == kArrayType);
    EnterArray();
    while (NextArrayValue()) {
        auto layer = parseLayer();
        if (layer) {
            staticFlag = staticFlag && layer->isStatic();
            comp->mRootLayer->mChildren.push_back(layer);
        }
    }
    comp->mRootLayer->setStatic(staticFlag);
}

model::Color LottieParserImpl::toColor(const char *str)
{
    model::Color color;
    auto         len = strlen(str);

    // some resource has empty color string
    // return a default color for those cases.
    if (len != 7 || str[0] != '#') return color;

    char tmp[3] = {'\0', '\0', '\0'};
    tmp[0] = str[1];
    tmp[1] = str[2];
    color.r = std::strtol(tmp, nullptr, 16) / 255.0f;

    tmp[0] = str[3];
    tmp[1] = str[4];
    color.g = std::strtol(tmp, nullptr, 16) / 255.0f;

    tmp[0] = str[5];
    tmp[1] = str[6];
    color.b = std::strtol(tmp, nullptr, 16) / 255.0f;

    return color;
}

model::MatteType LottieParserImpl::getMatteType()
{
    RAPIDJSON_ASSERT(PeekType() == kNumberType);
    switch (GetInt()) {
    case 1:
        return model::MatteType::Alpha;
        break;
    case 2:
        return model::MatteType::AlphaInv;
        break;
    case 3:
        return model::MatteType::Luma;
        break;
    case 4:
        return model::MatteType::LumaInv;
        break;
    default:
        return model::MatteType::None;
        break;
    }
}

model::Layer::Type LottieParserImpl::getLayerType()
{
    RAPIDJSON_ASSERT(PeekType() == kNumberType);
    switch (GetInt()) {
    case 0:
        return model::Layer::Type::Precomp;
        break;
    case 1:
        return model::Layer::Type::Solid;
        break;
    case 2:
        return model::Layer::Type::Image;
        break;
    case 3:
        return model::Layer::Type::Null;
        break;
    case 4:
        return model::Layer::Type::Shape;
        break;
    case 5:
        return model::Layer::Type::Text;
        break;
    default:
        return model::Layer::Type::Null;
        break;
    }
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/layers/shape.json
 *
 */
model::Layer *LottieParserImpl::parseLayer()
{
    RAPIDJSON_ASSERT(PeekType() == kObjectType);
    model::Layer *layer = allocator().make<model::Layer>();
    curLayerRef = layer;
    bool ddd = true;
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "ty")) { /* Type of layer*/
            layer->mLayerType = getLayerType();
        } else if (0 == strcmp(key, "nm")) { /*Layer name*/
            RAPIDJSON_ASSERT(PeekType() == kStringType);
            layer->setName(GetString());
        } else if (0 == strcmp(key, "ind")) { /*Layer index in AE. Used for
                                                 parenting and expressions.*/
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            layer->mId = GetInt();
        } else if (0 == strcmp(key, "ddd")) { /*3d layer */
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            ddd = GetInt();
        } else if (0 ==
                   strcmp(key,
                          "parent")) { /*Layer Parent. Uses "ind" of parent.*/
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            layer->mParentId = GetInt();
        } else if (0 == strcmp(key, "refId")) { /*preComp Layer reference id*/
            RAPIDJSON_ASSERT(PeekType() == kStringType);
            layer->extra()->mPreCompRefId = std::string(GetString());
            layer->mHasGradient = true;
            mLayersToUpdate.push_back(layer);
        } else if (0 == strcmp(key, "sr")) {  // "Layer Time Stretching"
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            layer->mTimeStreatch = GetDouble();
        } else if (0 == strcmp(key, "tm")) {  // time remapping
            parseProperty(layer->extra()->mTimeRemap);
        } else if (0 == strcmp(key, "ip")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            layer->mInFrame = std::lround(GetDouble());
        } else if (0 == strcmp(key, "op")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            layer->mOutFrame = std::lround(GetDouble());
        } else if (0 == strcmp(key, "st")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            layer->mStartFrame = GetDouble();
        } else if (0 == strcmp(key, "bm")) {
            layer->mBlendMode = getBlendMode();
        } else if (0 == strcmp(key, "ks")) {
            RAPIDJSON_ASSERT(PeekType() == kObjectType);
            EnterObject();
            layer->mTransform = parseTransformObject(ddd);
        } else if (0 == strcmp(key, "shapes")) {
            parseShapesAttr(layer);
        } else if (0 == strcmp(key, "w")) {
            layer->mLayerSize.setWidth(GetInt());
        } else if (0 == strcmp(key, "h")) {
            layer->mLayerSize.setHeight(GetInt());
        } else if (0 == strcmp(key, "sw")) {
            layer->mLayerSize.setWidth(GetInt());
        } else if (0 == strcmp(key, "sh")) {
            layer->mLayerSize.setHeight(GetInt());
        } else if (0 == strcmp(key, "sc")) {
            layer->extra()->mSolidColor = toColor(GetString());
        } else if (0 == strcmp(key, "tt")) {
            layer->mMatteType = getMatteType();
        } else if (0 == strcmp(key, "hasMask")) {
            layer->mHasMask = GetBool();
        } else if (0 == strcmp(key, "masksProperties")) {
            parseMaskProperty(layer);
        } else if (0 == strcmp(key, "ao")) {
            layer->mAutoOrient = GetInt();
        } else if (0 == strcmp(key, "hd")) {
            layer->setHidden(GetBool());
        } else {
#ifdef DEBUG_PARSER
            vWarning << "Layer Attribute Skipped : " << key;
#endif
            Skip(key);
        }
    }

    if (!layer->mTransform) {
        // not a valid layer
        return nullptr;
    }

    // make sure layer data is not corrupted.
    if (layer->hasParent() && (layer->id() == layer->parentId()))
        return nullptr;

    if (layer->mExtra) layer->mExtra->mCompRef = compRef;

    if (layer->hidden()) {
        // if layer is hidden, only data that is usefull is its
        // transform matrix(when it is a parent of some other layer)
        // so force it to be a Null Layer and release all resource.
        layer->setStatic(layer->mTransform->isStatic());
        layer->mLayerType = model::Layer::Type::Null;
        layer->mChildren = {};
        return layer;
    }

    // update the static property of layer
    bool staticFlag = true;
    for (const auto &child : layer->mChildren) {
        staticFlag &= child->isStatic();
    }

    if (layer->hasMask()) {
        for (const auto &mask : layer->mExtra->mMasks) {
            staticFlag &= mask->isStatic();
        }
    }

    layer->setStatic(staticFlag && layer->mTransform->isStatic());

    return layer;
}

void LottieParserImpl::parseMaskProperty(model::Layer *layer)
{
    RAPIDJSON_ASSERT(PeekType() == kArrayType);
    EnterArray();
    while (NextArrayValue()) {
        layer->extra()->mMasks.push_back(parseMaskObject());
    }
}

model::Mask *LottieParserImpl::parseMaskObject()
{
    auto obj = allocator().make<model::Mask>();

    RAPIDJSON_ASSERT(PeekType() == kObjectType);
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "inv")) {
            obj->mInv = GetBool();
        } else if (0 == strcmp(key, "mode")) {
            const char *str = GetString();
            if (!str) {
                obj->mMode = model::Mask::Mode::None;
                continue;
            }
            switch (str[0]) {
            case 'n':
                obj->mMode = model::Mask::Mode::None;
                break;
            case 'a':
                obj->mMode = model::Mask::Mode::Add;
                break;
            case 's':
                obj->mMode = model::Mask::Mode::Substarct;
                break;
            case 'i':
                obj->mMode = model::Mask::Mode::Intersect;
                break;
            case 'f':
                obj->mMode = model::Mask::Mode::Difference;
                break;
            default:
                obj->mMode = model::Mask::Mode::None;
                break;
            }
        } else if (0 == strcmp(key, "pt")) {
            parseShapeProperty(obj->mShape);
        } else if (0 == strcmp(key, "o")) {
            parseProperty(obj->mOpacity);
        } else {
            Skip(key);
        }
    }
    obj->mIsStatic = obj->mShape.isStatic() && obj->mOpacity.isStatic();
    return obj;
}

void LottieParserImpl::parseShapesAttr(model::Layer *layer)
{
    RAPIDJSON_ASSERT(PeekType() == kArrayType);
    EnterArray();
    while (NextArrayValue()) {
        parseObject(layer);
    }
}

model::Object *LottieParserImpl::parseObjectTypeAttr()
{
    RAPIDJSON_ASSERT(PeekType() == kStringType);
    const char *type = GetString();
    if (0 == strcmp(type, "gr")) {
        return parseGroupObject();
    } else if (0 == strcmp(type, "rc")) {
        return parseRectObject();
    } else if (0 == strcmp(type, "rd")) {
        curLayerRef->mHasRoundedCorner = true;
        return parseRoundedCorner();
    }  else if (0 == strcmp(type, "el")) {
        return parseEllipseObject();
    } else if (0 == strcmp(type, "tr")) {
        return parseTransformObject();
    } else if (0 == strcmp(type, "fl")) {
        return parseFillObject();
    } else if (0 == strcmp(type, "st")) {
        return parseStrokeObject();
    } else if (0 == strcmp(type, "gf")) {
        curLayerRef->mHasGradient = true;
        return parseGFillObject();
    } else if (0 == strcmp(type, "gs")) {
        curLayerRef->mHasGradient = true;
        return parseGStrokeObject();
    } else if (0 == strcmp(type, "sh")) {
        return parseShapeObject();
    } else if (0 == strcmp(type, "sr")) {
        return parsePolystarObject();
    } else if (0 == strcmp(type, "tm")) {
        curLayerRef->mHasPathOperator = true;
        return parseTrimObject();
    } else if (0 == strcmp(type, "rp")) {
        curLayerRef->mHasRepeater = true;
        return parseReapeaterObject();
    } else if (0 == strcmp(type, "mm")) {
        vWarning << "Merge Path is not supported yet";
        return nullptr;
    } else {
#ifdef DEBUG_PARSER
        vDebug << "The Object Type not yet handled = " << type;
#endif
        return nullptr;
    }
}

void LottieParserImpl::parseObject(model::Group *parent)
{
    RAPIDJSON_ASSERT(PeekType() == kObjectType);
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "ty")) {
            auto child = parseObjectTypeAttr();
            if (child && !child->hidden()) {
                if (child->type() == model::Object::Type::RoundedCorner) {
                    updateRoundedCorner(parent, static_cast<model::RoundedCorner *>(child));
                }
                parent->mChildren.push_back(child);
            }
        } else {
            Skip(key);
        }
    }
}

void LottieParserImpl::updateRoundedCorner(model::Group *group, model::RoundedCorner *rc)
{
    for(auto &e : group->mChildren)
    {
        if (e->type() == model::Object::Type::Rect) {
            static_cast<model::Rect *>(e)->mRoundedCorner = rc;
            if (!rc->isStatic()) {
                e->setStatic(false);
                group->setStatic(false);
                //@TODO need to propagate.
            }
        } else if ( e->type() == model::Object::Type::Group) {
            updateRoundedCorner(static_cast<model::Group *>(e), rc);
        }
    }
}

model::Object *LottieParserImpl::parseGroupObject()
{
    auto group = allocator().make<model::Group>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            group->setName(GetString());
        } else if (0 == strcmp(key, "it")) {
            RAPIDJSON_ASSERT(PeekType() == kArrayType);
            EnterArray();
            while (NextArrayValue()) {
                RAPIDJSON_ASSERT(PeekType() == kObjectType);
                parseObject(group);
            }
            if (group->mChildren.back()->type() ==
                model::Object::Type::Transform) {
                group->mTransform =
                    static_cast<model::Transform *>(group->mChildren.back());
                group->mChildren.pop_back();
            }
        } else {
            Skip(key);
        }
    }
    bool staticFlag = true;
    for (const auto &child : group->mChildren) {
        staticFlag &= child->isStatic();
    }

    if (group->mTransform) {
        group->setStatic(staticFlag && group->mTransform->isStatic());
    }

    return group;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/rect.json
 */
model::Rect *LottieParserImpl::parseRectObject()
{
    auto obj = allocator().make<model::Rect>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "p")) {
            parseProperty(obj->mPos);
        } else if (0 == strcmp(key, "s")) {
            parseProperty(obj->mSize);
        } else if (0 == strcmp(key, "r")) {
            parseProperty(obj->mRound);
        } else if (0 == strcmp(key, "d")) {
            obj->mDirection = GetInt();
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
            Skip(key);
        }
    }
    obj->setStatic(obj->mPos.isStatic() && obj->mSize.isStatic() &&
                   obj->mRound.isStatic());
    return obj;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/rect.json
 */
model::RoundedCorner *LottieParserImpl::parseRoundedCorner()
{
    auto obj = allocator().make<model::RoundedCorner>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "r")) {
            parseProperty(obj->mRadius);
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
            Skip(key);
        }
    }
    obj->setStatic(obj->mRadius.isStatic());
    return obj;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/ellipse.json
 */
model::Ellipse *LottieParserImpl::parseEllipseObject()
{
    auto obj = allocator().make<model::Ellipse>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "p")) {
            parseProperty(obj->mPos);
        } else if (0 == strcmp(key, "s")) {
            parseProperty(obj->mSize);
        } else if (0 == strcmp(key, "d")) {
            obj->mDirection = GetInt();
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
            Skip(key);
        }
    }
    obj->setStatic(obj->mPos.isStatic() && obj->mSize.isStatic());
    return obj;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/shape.json
 */
model::Path *LottieParserImpl::parseShapeObject()
{
    auto obj = allocator().make<model::Path>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "ks")) {
            parseShapeProperty(obj->mShape);
        } else if (0 == strcmp(key, "d")) {
            obj->mDirection = GetInt();
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
#ifdef DEBUG_PARSER
            vDebug << "Shape property ignored :" << key;
#endif
            Skip(key);
        }
    }
    obj->setStatic(obj->mShape.isStatic());

    return obj;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/star.json
 */
model::Polystar *LottieParserImpl::parsePolystarObject()
{
    auto obj = allocator().make<model::Polystar>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "p")) {
            parseProperty(obj->mPos);
        } else if (0 == strcmp(key, "pt")) {
            parseProperty(obj->mPointCount);
        } else if (0 == strcmp(key, "ir")) {
            parseProperty(obj->mInnerRadius);
        } else if (0 == strcmp(key, "is")) {
            parseProperty(obj->mInnerRoundness);
        } else if (0 == strcmp(key, "or")) {
            parseProperty(obj->mOuterRadius);
        } else if (0 == strcmp(key, "os")) {
            parseProperty(obj->mOuterRoundness);
        } else if (0 == strcmp(key, "r")) {
            parseProperty(obj->mRotation);
        } else if (0 == strcmp(key, "sy")) {
            int starType = GetInt();
            if (starType == 1) obj->mPolyType = model::Polystar::PolyType::Star;
            if (starType == 2)
                obj->mPolyType = model::Polystar::PolyType::Polygon;
        } else if (0 == strcmp(key, "d")) {
            obj->mDirection = GetInt();
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
#ifdef DEBUG_PARSER
            vDebug << "Polystar property ignored :" << key;
#endif
            Skip(key);
        }
    }
    obj->setStatic(
        obj->mPos.isStatic() && obj->mPointCount.isStatic() &&
        obj->mInnerRadius.isStatic() && obj->mInnerRoundness.isStatic() &&
        obj->mOuterRadius.isStatic() && obj->mOuterRoundness.isStatic() &&
        obj->mRotation.isStatic());

    return obj;
}

model::Trim::TrimType LottieParserImpl::getTrimType()
{
    RAPIDJSON_ASSERT(PeekType() == kNumberType);
    switch (GetInt()) {
    case 1:
        return model::Trim::TrimType::Simultaneously;
        break;
    case 2:
        return model::Trim::TrimType::Individually;
        break;
    default:
        RAPIDJSON_ASSERT(0);
        return model::Trim::TrimType::Simultaneously;
        break;
    }
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/trim.json
 */
model::Trim *LottieParserImpl::parseTrimObject()
{
    auto obj = allocator().make<model::Trim>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "s")) {
            parseProperty(obj->mStart);
        } else if (0 == strcmp(key, "e")) {
            parseProperty(obj->mEnd);
        } else if (0 == strcmp(key, "o")) {
            parseProperty(obj->mOffset);
        } else if (0 == strcmp(key, "m")) {
            obj->mTrimType = getTrimType();
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
#ifdef DEBUG_PARSER
            vDebug << "Trim property ignored :" << key;
#endif
            Skip(key);
        }
    }
    obj->setStatic(obj->mStart.isStatic() && obj->mEnd.isStatic() &&
                   obj->mOffset.isStatic());
    return obj;
}

void LottieParserImpl::getValue(model::Repeater::Transform &obj)
{
    EnterObject();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "a")) {
            parseProperty(obj.mAnchor);
        } else if (0 == strcmp(key, "p")) {
            parseProperty(obj.mPosition);
        } else if (0 == strcmp(key, "r")) {
            parseProperty(obj.mRotation);
        } else if (0 == strcmp(key, "s")) {
            parseProperty(obj.mScale);
        } else if (0 == strcmp(key, "so")) {
            parseProperty(obj.mStartOpacity);
        } else if (0 == strcmp(key, "eo")) {
            parseProperty(obj.mEndOpacity);
        } else {
            Skip(key);
        }
    }
}

model::Repeater *LottieParserImpl::parseReapeaterObject()
{
    auto obj = allocator().make<model::Repeater>();

    obj->setContent(allocator().make<model::Group>());

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "c")) {
            parseProperty(obj->mCopies);
            float maxCopy = 0.0;
            if (!obj->mCopies.isStatic()) {
                for (auto &keyFrame : obj->mCopies.animation().frames_) {
                    if (maxCopy < keyFrame.value_.start_)
                        maxCopy = keyFrame.value_.start_;
                    if (maxCopy < keyFrame.value_.end_)
                        maxCopy = keyFrame.value_.end_;
                }
            } else {
                maxCopy = obj->mCopies.value();
            }
            obj->mMaxCopies = maxCopy;
        } else if (0 == strcmp(key, "o")) {
            parseProperty(obj->mOffset);
        } else if (0 == strcmp(key, "tr")) {
            getValue(obj->mTransform);
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
#ifdef DEBUG_PARSER
            vDebug << "Repeater property ignored :" << key;
#endif
            Skip(key);
        }
    }
    obj->setStatic(obj->mCopies.isStatic() && obj->mOffset.isStatic() &&
                   obj->mTransform.isStatic());

    return obj;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/transform.json
 */
model::Transform *LottieParserImpl::parseTransformObject(bool ddd)
{
    auto objT = allocator().make<model::Transform>();

    auto obj = allocator().make<model::Transform::Data>();
    if (ddd) {
        obj->createExtraData();
        obj->mExtra->m3DData = true;
    }

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            objT->setName(GetString());
        } else if (0 == strcmp(key, "a")) {
            parseProperty(obj->mAnchor);
        } else if (0 == strcmp(key, "p")) {
            EnterObject();
            bool separate = false;
            while (const char *key = NextObjectKey()) {
                if (0 == strcmp(key, "k")) {
                    parsePropertyHelper(obj->mPosition);
                } else if (0 == strcmp(key, "s")) {
                    obj->createExtraData();
                    obj->mExtra->mSeparate = GetBool();
                    separate = true;
                } else if (separate && (0 == strcmp(key, "x"))) {
                    parseProperty(obj->mExtra->mSeparateX);
                } else if (separate && (0 == strcmp(key, "y"))) {
                    parseProperty(obj->mExtra->mSeparateY);
                } else {
                    Skip(key);
                }
            }
        } else if (0 == strcmp(key, "r")) {
            parseProperty(obj->mRotation);
        } else if (0 == strcmp(key, "s")) {
            parseProperty(obj->mScale);
        } else if (0 == strcmp(key, "o")) {
            parseProperty(obj->mOpacity);
        } else if (0 == strcmp(key, "hd")) {
            objT->setHidden(GetBool());
        } else if (0 == strcmp(key, "rx")) {
            parseProperty(obj->mExtra->m3DRx);
        } else if (0 == strcmp(key, "ry")) {
            parseProperty(obj->mExtra->m3DRy);
        } else if (0 == strcmp(key, "rz")) {
            parseProperty(obj->mExtra->m3DRz);
        } else {
            Skip(key);
        }
    }
    bool isStatic = obj->mAnchor.isStatic() && obj->mPosition.isStatic() &&
                    obj->mRotation.isStatic() && obj->mScale.isStatic() &&
                    obj->mOpacity.isStatic();
    if (obj->mExtra) {
        isStatic = isStatic && obj->mExtra->m3DRx.isStatic() &&
                   obj->mExtra->m3DRy.isStatic() &&
                   obj->mExtra->m3DRz.isStatic() &&
                   obj->mExtra->mSeparateX.isStatic() &&
                   obj->mExtra->mSeparateY.isStatic();
    }

    objT->set(obj, isStatic);

    return objT;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/fill.json
 */
model::Fill *LottieParserImpl::parseFillObject()
{
    auto obj = allocator().make<model::Fill>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "c")) {
            parseProperty(obj->mColor);
        } else if (0 == strcmp(key, "o")) {
            parseProperty(obj->mOpacity);
        } else if (0 == strcmp(key, "fillEnabled")) {
            obj->mEnabled = GetBool();
        } else if (0 == strcmp(key, "r")) {
            obj->mFillRule = getFillRule();
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
#ifdef DEBUG_PARSER
            vWarning << "Fill property skipped = " << key;
#endif
            Skip(key);
        }
    }
    obj->setStatic(obj->mColor.isStatic() && obj->mOpacity.isStatic());

    return obj;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/helpers/lineCap.json
 */
CapStyle LottieParserImpl::getLineCap()
{
    RAPIDJSON_ASSERT(PeekType() == kNumberType);
    switch (GetInt()) {
    case 1:
        return CapStyle::Flat;
        break;
    case 2:
        return CapStyle::Round;
        break;
    default:
        return CapStyle::Square;
        break;
    }
}

FillRule LottieParserImpl::getFillRule()
{
    RAPIDJSON_ASSERT(PeekType() == kNumberType);
    switch (GetInt()) {
    case 1:
        return FillRule::Winding;
        break;
    case 2:
        return FillRule::EvenOdd;
        break;
    default:
        return FillRule::Winding;
        break;
    }
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/helpers/lineJoin.json
 */
JoinStyle LottieParserImpl::getLineJoin()
{
    RAPIDJSON_ASSERT(PeekType() == kNumberType);
    switch (GetInt()) {
    case 1:
        return JoinStyle::Miter;
        break;
    case 2:
        return JoinStyle::Round;
        break;
    default:
        return JoinStyle::Bevel;
        break;
    }
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/stroke.json
 */
model::Stroke *LottieParserImpl::parseStrokeObject()
{
    auto obj = allocator().make<model::Stroke>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "c")) {
            parseProperty(obj->mColor);
        } else if (0 == strcmp(key, "o")) {
            parseProperty(obj->mOpacity);
        } else if (0 == strcmp(key, "w")) {
            parseProperty(obj->mWidth);
        } else if (0 == strcmp(key, "fillEnabled")) {
            obj->mEnabled = GetBool();
        } else if (0 == strcmp(key, "lc")) {
            obj->mCapStyle = getLineCap();
        } else if (0 == strcmp(key, "lj")) {
            obj->mJoinStyle = getLineJoin();
        } else if (0 == strcmp(key, "ml")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            obj->mMiterLimit = GetDouble();
        } else if (0 == strcmp(key, "d")) {
            parseDashProperty(obj->mDash);
        } else if (0 == strcmp(key, "hd")) {
            obj->setHidden(GetBool());
        } else {
#ifdef DEBUG_PARSER
            vWarning << "Stroke property skipped = " << key;
#endif
            Skip(key);
        }
    }
    obj->setStatic(obj->mColor.isStatic() && obj->mOpacity.isStatic() &&
                   obj->mWidth.isStatic() && obj->mDash.isStatic());
    return obj;
}

void LottieParserImpl::parseGradientProperty(model::Gradient *obj,
                                             const char *     key)
{
    if (0 == strcmp(key, "t")) {
        RAPIDJSON_ASSERT(PeekType() == kNumberType);
        obj->mGradientType = GetInt();
    } else if (0 == strcmp(key, "o")) {
        parseProperty(obj->mOpacity);
    } else if (0 == strcmp(key, "s")) {
        parseProperty(obj->mStartPoint);
    } else if (0 == strcmp(key, "e")) {
        parseProperty(obj->mEndPoint);
    } else if (0 == strcmp(key, "h")) {
        parseProperty(obj->mHighlightLength);
    } else if (0 == strcmp(key, "a")) {
        parseProperty(obj->mHighlightAngle);
    } else if (0 == strcmp(key, "g")) {
        EnterObject();
        while (const char *key = NextObjectKey()) {
            if (0 == strcmp(key, "k")) {
                parseProperty(obj->mGradient);
            } else if (0 == strcmp(key, "p")) {
                obj->mColorPoints = GetInt();
            } else {
                Skip(nullptr);
            }
        }
    } else if (0 == strcmp(key, "hd")) {
        obj->setHidden(GetBool());
    } else {
#ifdef DEBUG_PARSER
        vWarning << "Gradient property skipped = " << key;
#endif
        Skip(key);
    }
    obj->setStatic(
        obj->mOpacity.isStatic() && obj->mStartPoint.isStatic() &&
        obj->mEndPoint.isStatic() && obj->mHighlightAngle.isStatic() &&
        obj->mHighlightLength.isStatic() && obj->mGradient.isStatic());
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/gfill.json
 */
model::GradientFill *LottieParserImpl::parseGFillObject()
{
    auto obj = allocator().make<model::GradientFill>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "r")) {
            obj->mFillRule = getFillRule();
        } else {
            parseGradientProperty(obj, key);
        }
    }
    return obj;
}

void LottieParserImpl::parseDashProperty(model::Dash &dash)
{
    RAPIDJSON_ASSERT(PeekType() == kArrayType);
    EnterArray();
    while (NextArrayValue()) {
        RAPIDJSON_ASSERT(PeekType() == kObjectType);
        EnterObject();
        while (const char *key = NextObjectKey()) {
            if (0 == strcmp(key, "v")) {
                dash.mData.emplace_back();
                parseProperty(dash.mData.back());
            } else {
                Skip(key);
            }
        }
    }
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/shapes/gstroke.json
 */
model::GradientStroke *LottieParserImpl::parseGStrokeObject()
{
    auto obj = allocator().make<model::GradientStroke>();

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "nm")) {
            obj->setName(GetString());
        } else if (0 == strcmp(key, "w")) {
            parseProperty(obj->mWidth);
        } else if (0 == strcmp(key, "lc")) {
            obj->mCapStyle = getLineCap();
        } else if (0 == strcmp(key, "lj")) {
            obj->mJoinStyle = getLineJoin();
        } else if (0 == strcmp(key, "ml")) {
            RAPIDJSON_ASSERT(PeekType() == kNumberType);
            obj->mMiterLimit = GetDouble();
        } else if (0 == strcmp(key, "d")) {
            parseDashProperty(obj->mDash);
        } else {
            parseGradientProperty(obj, key);
        }
    }

    obj->setStatic(obj->isStatic() && obj->mWidth.isStatic() &&
                   obj->mDash.isStatic());
    return obj;
}

void LottieParserImpl::getValue(std::vector<VPointF> &v)
{
    RAPIDJSON_ASSERT(PeekType() == kArrayType);
    EnterArray();
    while (NextArrayValue()) {
        RAPIDJSON_ASSERT(PeekType() == kArrayType);
        EnterArray();
        VPointF pt;
        getValue(pt);
        v.push_back(pt);
    }
}

void LottieParserImpl::getValue(VPointF &pt)
{
    float val[4] = {0.f};
    int   i = 0;

    if (PeekType() == kArrayType) EnterArray();

    while (NextArrayValue()) {
        const auto value = GetDouble();
        if (i < 4) {
            val[i++] = value;
        }
    }
    pt.setX(val[0]);
    pt.setY(val[1]);
}

void LottieParserImpl::getValue(float &val)
{
    if (PeekType() == kArrayType) {
        EnterArray();
        if (NextArrayValue()) val = GetDouble();
        // discard rest
        while (NextArrayValue()) {
            GetDouble();
        }
    } else if (PeekType() == kNumberType) {
        val = GetDouble();
    } else {
        RAPIDJSON_ASSERT(0);
    }
}

void LottieParserImpl::getValue(model::Color &color)
{
    float val[4] = {0.f};
    int   i = 0;
    if (PeekType() == kArrayType) EnterArray();

    while (NextArrayValue()) {
        const auto value = GetDouble();
        if (i < 4) {
            val[i++] = value;
        }
    }

    color.r = val[2];
    color.g = val[1];
    color.b = val[0];
    color.colorMap = colorMap;
}

void LottieParserImpl::getValue(model::Gradient::Data &grad)
{
    if (PeekType() == kArrayType) EnterArray();

    while (NextArrayValue()) {
        grad.mGradient.push_back(GetDouble());
    }
}

void LottieParserImpl::getValue(int &val)
{
    if (PeekType() == kArrayType) {
        EnterArray();
        while (NextArrayValue()) {
            val = GetInt();
        }
    } else if (PeekType() == kNumberType) {
        val = GetInt();
    } else {
        RAPIDJSON_ASSERT(0);
    }
}

void LottieParserImpl::parsePathInfo()
{
    mPathInfo.reset();

    /*
     * The shape object could be wrapped by a array
     * if its part of the keyframe object
     */
    bool arrayWrapper = (PeekType() == kArrayType);
    if (arrayWrapper) EnterArray();

    RAPIDJSON_ASSERT(PeekType() == kObjectType);
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "i")) {
            getValue(mPathInfo.mInPoint);
        } else if (0 == strcmp(key, "o")) {
            getValue(mPathInfo.mOutPoint);
        } else if (0 == strcmp(key, "v")) {
            getValue(mPathInfo.mVertices);
        } else if (0 == strcmp(key, "c")) {
            mPathInfo.mClosed = GetBool();
        } else {
            RAPIDJSON_ASSERT(0);
            Skip(nullptr);
        }
    }
    // exit properly from the array
    if (arrayWrapper) NextArrayValue();

    mPathInfo.convert();
}

void LottieParserImpl::getValue(model::PathData &obj)
{
    parsePathInfo();
    obj.mPoints = mPathInfo.mResult;
    obj.mClosed = mPathInfo.mClosed;
}

VPointF LottieParserImpl::parseInperpolatorPoint()
{
    VPointF cp;
    RAPIDJSON_ASSERT(PeekType() == kObjectType);
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "x")) {
            getValue(cp.rx());
        }
        if (0 == strcmp(key, "y")) {
            getValue(cp.ry());
        }
    }
    return cp;
}

template <typename T>
bool LottieParserImpl::parseKeyFrameValue(
    const char *key, model::Value<T, model::Position> &value)
{
    if (0 == strcmp(key, "ti")) {
        value.hasTangent_ = true;
        getValue(value.inTangent_);
    } else if (0 == strcmp(key, "to")) {
        value.hasTangent_ = true;
        getValue(value.outTangent_);
    } else {
        return false;
    }
    return true;
}

VInterpolator *LottieParserImpl::interpolator(VPointF     inTangent,
                                              VPointF     outTangent,
                                              std::string key)
{
    if (key.empty()) {
        std::array<char, 20> temp;
        snprintf(temp.data(), temp.size(), "%.2f_%.2f_%.2f_%.2f", inTangent.x(),
                 inTangent.y(), outTangent.x(), outTangent.y());
        key = temp.data();
    }

    auto search = mInterpolatorCache.find(key);

    if (search != mInterpolatorCache.end()) {
        return search->second;
    }

    auto obj = allocator().make<VInterpolator>(outTangent, inTangent);
    mInterpolatorCache[std::move(key)] = obj;
    return obj;
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/properties/multiDimensionalKeyframed.json
 */
template <typename T, typename Tag>
void LottieParserImpl::parseKeyFrame(model::KeyFrames<T, Tag> &obj)
{
    struct ParsedField {
        std::string interpolatorKey;
        bool        interpolator{false};
        bool        value{false};
        bool        hold{false};
        bool        noEndValue{true};
    };

    EnterObject();
    ParsedField                              parsed;
    typename model::KeyFrames<T, Tag>::Frame keyframe;
    VPointF                                  inTangent;
    VPointF                                  outTangent;

    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "i")) {
            parsed.interpolator = true;
            inTangent = parseInperpolatorPoint();
        } else if (0 == strcmp(key, "o")) {
            outTangent = parseInperpolatorPoint();
        } else if (0 == strcmp(key, "t")) {
            keyframe.start_ = GetDouble();
        } else if (0 == strcmp(key, "s")) {
            parsed.value = true;
            getValue(keyframe.value_.start_);
            continue;
        } else if (0 == strcmp(key, "e")) {
            parsed.noEndValue = false;
            getValue(keyframe.value_.end_);
            continue;
        } else if (0 == strcmp(key, "n")) {
            if (PeekType() == kStringType) {
                parsed.interpolatorKey = GetString();
            } else {
                RAPIDJSON_ASSERT(PeekType() == kArrayType);
                EnterArray();
                while (NextArrayValue()) {
                    RAPIDJSON_ASSERT(PeekType() == kStringType);
                    if (parsed.interpolatorKey.empty()) {
                        parsed.interpolatorKey = GetString();
                    } else {
                        // skip rest of the string
                        GetString();
                    }
                }
            }
            continue;
        } else if (parseKeyFrameValue(key, keyframe.value_)) {
            continue;
        } else if (0 == strcmp(key, "h")) {
            parsed.hold = GetInt();
            continue;
        } else {
#ifdef DEBUG_PARSER
            vDebug << "key frame property skipped = " << key;
#endif
            Skip(key);
        }
    }

    auto &list = obj.frames_;
    if (!list.empty()) {
        // update the endFrame value of current keyframe
        list.back().end_ = keyframe.start_;
        // if no end value provided, copy start value to previous frame
        if (parsed.value && parsed.noEndValue) {
            list.back().value_.end_ = keyframe.value_.start_;
        }
    }

    if (parsed.hold) {
        keyframe.value_.end_ = keyframe.value_.start_;
        keyframe.end_ = keyframe.start_;
        list.push_back(std::move(keyframe));
    } else if (parsed.interpolator) {
        keyframe.interpolator_ = interpolator(
            inTangent, outTangent, std::move(parsed.interpolatorKey));
        list.push_back(std::move(keyframe));
    } else {
        // its the last frame discard.
    }
}

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/properties/shapeKeyframed.json
 */

/*
 * https://github.com/airbnb/lottie-web/blob/master/docs/json/properties/shape.json
 */
void LottieParserImpl::parseShapeProperty(model::Property<model::PathData> &obj)
{
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "k")) {
            if (PeekType() == kArrayType) {
                EnterArray();
                while (NextArrayValue()) {
                    RAPIDJSON_ASSERT(PeekType() == kObjectType);
                    parseKeyFrame(obj.animation());
                }
            } else {
                if (!obj.isStatic()) {
                    RAPIDJSON_ASSERT(false);
                    st_ = kError;
                    return;
                }
                getValue(obj.value());
            }
        } else {
#ifdef DEBUG_PARSER
            vDebug << "shape property ignored = " << key;
#endif
            Skip(nullptr);
        }
    }
    obj.cache();
}

template <typename T, typename Tag>
void LottieParserImpl::parsePropertyHelper(model::Property<T, Tag> &obj)
{
    if (PeekType() == kNumberType) {
        if (!obj.isStatic()) {
            RAPIDJSON_ASSERT(false);
            st_ = kError;
            return;
        }
        /*single value property with no animation*/
        getValue(obj.value());
    } else {
        RAPIDJSON_ASSERT(PeekType() == kArrayType);
        EnterArray();
        while (NextArrayValue()) {
            /* property with keyframe info*/
            if (PeekType() == kObjectType) {
                parseKeyFrame(obj.animation());
            } else {
                /* Read before modifying.
                 * as there is no way of knowing if the
                 * value of the array is either array of numbers
                 * or array of object without entering the array
                 * thats why this hack is there
                 */
                RAPIDJSON_ASSERT(PeekType() == kNumberType);
                if (!obj.isStatic()) {
                    RAPIDJSON_ASSERT(false);
                    st_ = kError;
                    return;
                }
                /*multi value property with no animation*/
                getValue(obj.value());
                /*break here as we already reached end of array*/
                break;
            }
        }
        obj.cache();
    }
}

/*
 * https://github.com/airbnb/lottie-web/tree/master/docs/json/properties
 */
template <typename T>
void LottieParserImpl::parseProperty(model::Property<T> &obj)
{
    EnterObject();
    while (const char *key = NextObjectKey()) {
        if (0 == strcmp(key, "k")) {
            parsePropertyHelper(obj);
        } else {
            Skip(key);
        }
    }
}

#ifdef LOTTIE_DUMP_TREE_SUPPORT

class ObjectInspector {
public:
    void visit(model::Composition *obj, std::string level)
    {
        vDebug << " { " << level << "Composition:: a: " << !obj->isStatic()
               << ", v: " << obj->mVersion << ", stFm: " << obj->startFrame()
               << ", endFm: " << obj->endFrame()
               << ", W: " << obj->size().width()
               << ", H: " << obj->size().height() << "\n";
        level.append("\t");
        visit(obj->mRootLayer, level);
        level.erase(level.end() - 1, level.end());
        vDebug << " } " << level << "Composition End\n";
    }
    void visit(model::Layer *obj, std::string level)
    {
        vDebug << level << "{ " << layerType(obj->mLayerType)
               << ", name: " << obj->name() << ", id:" << obj->mId
               << " Pid:" << obj->mParentId << ", a:" << !obj->isStatic()
               << ", " << matteType(obj->mMatteType)
               << ", mask:" << obj->hasMask() << ", inFm:" << obj->mInFrame
               << ", outFm:" << obj->mOutFrame << ", stFm:" << obj->mStartFrame
               << ", ts:" << obj->mTimeStreatch << ", ao:" << obj->autoOrient()
               << ", W:" << obj->layerSize().width()
               << ", H:" << obj->layerSize().height();

        if (obj->mLayerType == model::Layer::Type::Image)
            vDebug << level << "\t{ "
                   << "ImageInfo:"
                   << " W :" << obj->extra()->mAsset->mWidth
                   << ", H :" << obj->extra()->mAsset->mHeight << " }"
                   << "\n";
        else {
            vDebug << level;
        }
        visitChildren(static_cast<model::Group *>(obj), level);
        vDebug << level << "} " << layerType(obj->mLayerType).c_str()
               << ", id: " << obj->mId << "\n";
    }
    void visitChildren(model::Group *obj, std::string level)
    {
        level.append("\t");
        for (const auto &child : obj->mChildren) visit(child, level);
        if (obj->mTransform) visit(obj->mTransform, level);
    }

    void visit(model::Object *obj, std::string level)
    {
        switch (obj->type()) {
        case model::Object::Type::Repeater: {
            auto r = static_cast<model::Repeater *>(obj);
            vDebug << level << "{ Repeater: name: " << obj->name()
                   << " , a:" << !obj->isStatic()
                   << ", copies:" << r->maxCopies()
                   << ", offset:" << r->offset(0);
            visitChildren(r->mContent, level);
            vDebug << level << "} Repeater";
            break;
        }
        case model::Object::Type::Group: {
            vDebug << level << "{ Group: name: " << obj->name()
                   << " , a:" << !obj->isStatic();
            visitChildren(static_cast<model::Group *>(obj), level);
            vDebug << level << "} Group";
            break;
        }
        case model::Object::Type::Layer: {
            visit(static_cast<model::Layer *>(obj), level);
            break;
        }
        case model::Object::Type::Trim: {
            vDebug << level << "{ Trim: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::Rect: {
            vDebug << level << "{ Rect: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::RoundedCorner: {
            vDebug << level << "{ RoundedCorner: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::Ellipse: {
            vDebug << level << "{ Ellipse: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::Path: {
            vDebug << level << "{ Shape: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::Polystar: {
            vDebug << level << "{ Polystar: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::Transform: {
            vDebug << level << "{ Transform: name: " << obj->name()
                   << " , a: " << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::Stroke: {
            vDebug << level << "{ Stroke: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::GStroke: {
            vDebug << level << "{ GStroke: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::Fill: {
            vDebug << level << "{ Fill: name: " << obj->name()
                   << " , a:" << !obj->isStatic() << " }";
            break;
        }
        case model::Object::Type::GFill: {
            auto f = static_cast<model::GradientFill *>(obj);
            vDebug << level << "{ GFill: name: " << obj->name()
                   << " , a:" << !f->isStatic() << ", ty:" << f->mGradientType
                   << ", s:" << f->mStartPoint.value(0)
                   << ", e:" << f->mEndPoint.value(0) << " }";
            break;
        }
        default:
            break;
        }
    }

    std::string matteType(model::MatteType type)
    {
        switch (type) {
        case model::MatteType::None:
            return "Matte::None";
            break;
        case model::MatteType::Alpha:
            return "Matte::Alpha";
            break;
        case model::MatteType::AlphaInv:
            return "Matte::AlphaInv";
            break;
        case model::MatteType::Luma:
            return "Matte::Luma";
            break;
        case model::MatteType::LumaInv:
            return "Matte::LumaInv";
            break;
        default:
            return "Matte::Unknown";
            break;
        }
    }
    std::string layerType(model::Layer::Type type)
    {
        switch (type) {
        case model::Layer::Type::Precomp:
            return "Layer::Precomp";
            break;
        case model::Layer::Type::Null:
            return "Layer::Null";
            break;
        case model::Layer::Type::Shape:
            return "Layer::Shape";
            break;
        case model::Layer::Type::Solid:
            return "Layer::Solid";
            break;
        case model::Layer::Type::Image:
            return "Layer::Image";
            break;
        case model::Layer::Type::Text:
            return "Layer::Text";
            break;
        default:
            return "Layer::Unknown";
            break;
        }
    }
};

#endif

std::shared_ptr<model::Composition> model::parse(char *             str,
                                                 std::string        dir_path,
                                                 std::map<int32_t, int32_t> *colorReplacement)
{
    LottieParserImpl obj(str, std::move(dir_path), colorReplacement);

    if (obj.VerifyType()) {
        obj.parseComposition();
        auto composition = obj.composition();
        if (composition) {
            composition->processRepeaterObjects();
            composition->updateStats();

#ifdef LOTTIE_DUMP_TREE_SUPPORT
            ObjectInspector inspector;
            inspector.visit(composition.get(), "");
#endif

            return composition;
        }
    }

    vWarning << "Input data is not Lottie format!";
    return {};
}

RAPIDJSON_DIAG_POP
