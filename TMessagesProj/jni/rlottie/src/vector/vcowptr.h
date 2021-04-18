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

#ifndef VCOWPTR_H
#define VCOWPTR_H

#include <cassert>
#include <atomic>

template <typename T>
class vcow_ptr {
    struct model {
        std::atomic<std::size_t> mRef{1};

        model() = default;

        template <class... Args>
        explicit model(Args&&... args) : mValue(std::forward<Args>(args)...){}
        explicit model(const T& other) : mValue(other){}

        T mValue;
    };
    model* mModel;

public:
    using element_type = T;

    vcow_ptr()
    {
        static model default_s;
        mModel = &default_s;
        ++mModel->mRef;
    }

    ~vcow_ptr()
    {
        if (mModel && (--mModel->mRef == 0)) delete mModel;
    }

    template <class... Args>
    explicit vcow_ptr(Args&&... args) : mModel(new model(std::forward<Args>(args)...))
    {
    }

    vcow_ptr(const vcow_ptr& x) noexcept : mModel(x.mModel)
    {
        assert(mModel);
        ++mModel->mRef;
    }
    vcow_ptr(vcow_ptr&& x) noexcept : mModel(x.mModel)
    {
        assert(mModel);
        x.mModel = nullptr;
    }

    auto operator=(const vcow_ptr& x) noexcept -> vcow_ptr&
    {
        *this = vcow_ptr(x);
        return *this;
    }

    auto operator=(vcow_ptr&& x) noexcept -> vcow_ptr&
    {
        auto tmp = std::move(x);
        swap(*this, tmp);
        return *this;
    }

    auto operator*() const noexcept -> const element_type& { return read(); }

    auto operator-> () const noexcept -> const element_type* { return &read(); }

    std::size_t refCount() const noexcept
    {
        assert(mModel);

        return mModel->mRef;
    }

    bool unique() const noexcept
    {
        assert(mModel);

        return mModel->mRef == 1;
    }

    auto write() -> element_type&
    {
        if (!unique()) *this = vcow_ptr(read());

        return mModel->mValue;
    }

    auto read() const noexcept -> const element_type&
    {
        assert(mModel);

        return mModel->mValue;
    }

    friend inline void swap(vcow_ptr& x, vcow_ptr& y) noexcept
    {
        std::swap(x.mModel, y.mModel);
    }
};

#endif  // VCOWPTR_H
