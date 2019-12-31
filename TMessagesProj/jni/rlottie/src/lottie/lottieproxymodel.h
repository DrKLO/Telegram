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

#ifndef LOTTIEPROXYMODEL_H
#define LOTTIEPROXYMODEL_H

#include<bitset>
#include<algorithm>
#include<cassert>
#include "lottiemodel.h"
#include "rlottie.h"

// Naive way to implement std::variant
// refactor it when we move to c++17
// users should make sure proper combination
// of id and value are passed while creating the object.
class LOTVariant
{
public:
    using ValueFunc = std::function<float(const rlottie::FrameInfo &)>;
    using ColorFunc = std::function<rlottie::Color(const rlottie::FrameInfo &)>;
    using PointFunc = std::function<rlottie::Point(const rlottie::FrameInfo &)>;
    using SizeFunc = std::function<rlottie::Size(const rlottie::FrameInfo &)>;

    LOTVariant(rlottie::Property prop, const ValueFunc &v):mPropery(prop), mTag(Value)
    {
        construct(impl.valueFunc, v);
    }

    LOTVariant(rlottie::Property prop, ValueFunc &&v):mPropery(prop), mTag(Value)
    {
        moveConstruct(impl.valueFunc, std::move(v));
    }

    LOTVariant(rlottie::Property prop, const ColorFunc &v):mPropery(prop), mTag(Color)
    {
        construct(impl.colorFunc, v);
    }

    LOTVariant(rlottie::Property prop, ColorFunc &&v):mPropery(prop), mTag(Color)
    {
        moveConstruct(impl.colorFunc, std::move(v));
    }

    LOTVariant(rlottie::Property prop, const PointFunc &v):mPropery(prop), mTag(Point)
    {
        construct(impl.pointFunc, v);
    }

    LOTVariant(rlottie::Property prop, PointFunc &&v):mPropery(prop), mTag(Point)
    {
        moveConstruct(impl.pointFunc, std::move(v));
    }

    LOTVariant(rlottie::Property prop, const SizeFunc &v):mPropery(prop), mTag(Size)
    {
        construct(impl.sizeFunc, v);
    }

    LOTVariant(rlottie::Property prop, SizeFunc &&v):mPropery(prop), mTag(Size)
    {
        moveConstruct(impl.sizeFunc, std::move(v));
    }

    rlottie::Property property() const { return mPropery; }

    const ColorFunc& color() const
    {
        assert(mTag == Color);
        return impl.colorFunc;
    }

    const ValueFunc& value() const
    {
        assert(mTag == Value);
        return impl.valueFunc;
    }

    const PointFunc& point() const
    {
        assert(mTag == Point);
        return impl.pointFunc;
    }

    const SizeFunc& size() const
    {
        assert(mTag == Size);
        return impl.sizeFunc;
    }

    LOTVariant() = default;
    ~LOTVariant() noexcept {Destroy();}
    LOTVariant(const LOTVariant& other) { Copy(other);}
    LOTVariant(LOTVariant&& other) noexcept { Move(std::move(other));}
    LOTVariant& operator=(LOTVariant&& other) { Destroy(); Move(std::move(other)); return *this;}
    LOTVariant& operator=(const LOTVariant& other) { Destroy(); Copy(other); return *this;}
private:
    template <typename T>
    void construct(T& member, const T& val)
    {
        new (&member) T(val);
    }

    template <typename T>
    void moveConstruct(T& member, T&& val)
    {
        new (&member) T(std::move(val));
    }

    void Move(LOTVariant&& other)
    {
        switch (other.mTag) {
        case Type::Value:
            moveConstruct(impl.valueFunc, std::move(other.impl.valueFunc));
            break;
        case Type::Color:
            moveConstruct(impl.colorFunc, std::move(other.impl.colorFunc));
            break;
        case Type::Point:
            moveConstruct(impl.pointFunc, std::move(other.impl.pointFunc));
            break;
        case Type::Size:
            moveConstruct(impl.sizeFunc, std::move(other.impl.sizeFunc));
            break;
        default:
            break;
        }
        mTag = other.mTag;
        mPropery = other.mPropery;
        other.mTag = MonoState;
    }

    void Copy(const LOTVariant& other)
    {
        switch (other.mTag) {
        case Type::Value:
            construct(impl.valueFunc, other.impl.valueFunc);
            break;
        case Type::Color:
            construct(impl.colorFunc, other.impl.colorFunc);
            break;
        case Type::Point:
            construct(impl.pointFunc, other.impl.pointFunc);
            break;
        case Type::Size:
            construct(impl.sizeFunc, other.impl.sizeFunc);
            break;
        default:
            break;
        }
        mTag = other.mTag;
        mPropery = other.mPropery;
    }

    void Destroy()
    {
        switch(mTag) {
        case MonoState: {
            break;
        }
        case Value: {
            impl.valueFunc.~ValueFunc();
            break;
        }
        case Color: {
            impl.colorFunc.~ColorFunc();
            break;
        }
        case Point: {
            impl.pointFunc.~PointFunc();
            break;
        }
        case Size: {
            impl.sizeFunc.~SizeFunc();
            break;
        }
        }
    }

    enum Type {MonoState, Value, Color, Point , Size};
    rlottie::Property mPropery;
    Type              mTag{MonoState};
    union details{
      ColorFunc   colorFunc;
      ValueFunc   valueFunc;
      PointFunc   pointFunc;
      SizeFunc    sizeFunc;
      details(){}
      ~details(){}
    }impl;
};

class LOTFilter
{
public:
    void addValue(LOTVariant &value)
    {
        uint index = static_cast<uint>(value.property());
        if (mBitset.test(index)) {
            std::replace_if(mFilters.begin(),
                            mFilters.end(),
                            [&value](const LOTVariant &e) {return e.property() == value.property();},
                            value);
        } else {
            mBitset.set(index);
            mFilters.push_back(value);
        }
    }

    void removeValue(LOTVariant &value)
    {
        uint index = static_cast<uint>(value.property());
        if (mBitset.test(index)) {
            mBitset.reset(index);
            mFilters.erase(std::remove_if(mFilters.begin(),
                                          mFilters.end(),
                                          [&value](const LOTVariant &e) {return e.property() == value.property();}),
                           mFilters.end());
        }
    }
    bool hasFilter(rlottie::Property prop) const
    {
        return mBitset.test(static_cast<uint>(prop));
    }
    LottieColor color(rlottie::Property prop, int frame) const
    {
        rlottie::FrameInfo info(frame);
        rlottie::Color col = data(prop).color()(info);
        return LottieColor(col.r(), col.g(), col.b(), nullptr);
    }
    float opacity(rlottie::Property prop, int frame) const
    {
        rlottie::FrameInfo info(frame);
        float val = data(prop).value()(info);
        return val/100;
    }
    float value(rlottie::Property prop, int frame) const
    {
        rlottie::FrameInfo info(frame);
        return data(prop).value()(info);
    }
private:
    const LOTVariant& data(rlottie::Property prop) const
    {
        auto result = std::find_if(mFilters.begin(),
                                   mFilters.end(),
                                   [prop](const LOTVariant &e){return e.property() == prop;});
        return *result;
    }
    std::bitset<32>            mBitset{0};
    std::vector<LOTVariant>    mFilters;
};

template <typename T>
class LOTProxyModel
{
public:
    LOTProxyModel(T *model): _modelData(model) {}
    LOTFilter& filter() {return mFilter;}
    const std::string & name() const {return _modelData->name();}
    LottieColor color(int frame) const
    {
        if (mFilter.hasFilter(rlottie::Property::Color)) {
            return mFilter.color(rlottie::Property::Color, frame);
        }
        return _modelData->color(frame);
    }
    float opacity(int frame) const
    {
        if (mFilter.hasFilter(rlottie::Property::StrokeOpacity)) {
            return mFilter.opacity(rlottie::Property::StrokeOpacity, frame);
        }
        return _modelData->opacity(frame);
    }
    float strokeWidth(int frame) const
    {
        if (mFilter.hasFilter(rlottie::Property::StrokeWidth)) {
            return mFilter.value(rlottie::Property::StrokeWidth, frame);
        }
        return _modelData->strokeWidth(frame);
    }
    float meterLimit() const {return _modelData->meterLimit();}
    CapStyle capStyle() const {return _modelData->capStyle();}
    JoinStyle joinStyle() const {return _modelData->joinStyle();}
    bool hasDashInfo() const { return _modelData->hasDashInfo();}
    int getDashInfo(int frameNo, float *array) const {return _modelData->getDashInfo(frameNo, array);}

private:
    T                         *_modelData;
    LOTFilter                  mFilter;
};

template <>
class LOTProxyModel<LOTFillData>
{
public:
    LOTProxyModel(LOTFillData *model): _modelData(model) {}
    LOTFilter& filter() {return mFilter;}
    const std::string & name() const {return _modelData->name();}
    LottieColor color(int frame) const
    {
        if (mFilter.hasFilter(rlottie::Property::Color)) {
            return mFilter.color(rlottie::Property::Color, frame);
        }
        return _modelData->color(frame);
    }
    float opacity(int frame) const
    {
        if (mFilter.hasFilter(rlottie::Property::FillOpacity)) {
            return mFilter.opacity(rlottie::Property::FillOpacity, frame);
        }
        return _modelData->opacity(frame);
    }
    FillRule fillRule() const {return _modelData->fillRule();}
private:
    LOTFillData               *_modelData;
    LOTFilter                  mFilter;
};

#endif // LOTTIEITEM_H
