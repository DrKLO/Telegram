#ifndef VSHAREDPTR_H
#define VSHAREDPTR_H

#include <cassert>
#include <memory>
#include <atomic>

template <typename T, typename Rc>
class vshared_ptr {
    struct model {
        Rc mRef{1};

        model() = default;

        template <class... Args>
        explicit model(Args&&... args) : mValue(std::forward<Args>(args)...){}
        explicit model(const T& other) : mValue(other){}

        T mValue;
    };
    model* mModel{nullptr};

public:
    using element_type = T;

    vshared_ptr() = default;

    ~vshared_ptr()
    {
        unref();
    }

    template <class... Args>
    explicit vshared_ptr(Args&&... args) : mModel(new model(std::forward<Args>(args)...))
    {
    }

    vshared_ptr(const vshared_ptr& x) noexcept : vshared_ptr()
    {
        if (x.mModel)  {
            mModel = x.mModel;
            ++mModel->mRef;
        }
    }

    vshared_ptr(vshared_ptr&& x) noexcept : vshared_ptr()
    {
        if (x.mModel)  {
            mModel = x.mModel;
            x.mModel = nullptr;
        }
    }

    auto operator=(const vshared_ptr& x) noexcept -> vshared_ptr&
    {
        unref();
        mModel = x.mModel;
        ref();
        return *this;
    }

    auto operator=(vshared_ptr&& x) noexcept -> vshared_ptr&
    {
        unref();
        mModel = x.mModel;
        x.mModel = nullptr;
        return *this;
    }

    operator bool() const noexcept {
      return mModel != nullptr;
    }

    auto operator*() const noexcept -> element_type& { return read(); }

    auto operator-> () const noexcept -> element_type* { return &read(); }

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

private:

    auto read() const noexcept -> element_type&
    {
        assert(mModel);

        return mModel->mValue;
    }

    void ref()
    {
        if (mModel) ++mModel->mRef;
    }

    void unref()
    {
        if (mModel && (--mModel->mRef == 0)) {
            delete mModel;
            mModel = nullptr;
        }
    }
};

// atomic ref counted pointer implementation.
template < typename T>
using arc_ptr = vshared_ptr<T, std::atomic<std::size_t>>;

// ref counter pointer implementation.
template < typename T>
using rc_ptr = vshared_ptr<T, std::size_t>;

#endif // VSHAREDPTR_H
