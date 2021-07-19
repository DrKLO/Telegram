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

#ifndef VCOWPTR_H
#define VCOWPTR_H

#include <assert.h>
#include "vglobal.h"

template <typename T>
class vcow_ptr {
    struct model {
        std::atomic<std::size_t> mRef{1};

        model() = default;

        template <class... Args>
        explicit model(Args&&... args) : mValue(std::forward<Args>(args)...)
        {
        }

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
    vcow_ptr(Args&&... args) : mModel(new model(std::forward<Args>(args)...))
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
        return *this = vcow_ptr(x);
    }

    auto operator=(vcow_ptr&& x) noexcept -> vcow_ptr&
    {
        auto tmp = std::move(x);
        swap(*this, tmp);
        return *this;
    }

    auto operator*() const noexcept -> const element_type& { return read(); }

    auto operator-> () const noexcept -> const element_type* { return &read(); }

    int refCount() const noexcept
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
