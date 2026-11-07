#ifndef LITEHTML_PIXEL_TYPE_H
#define LITEHTML_PIXEL_TYPE_H

#include <cstdlib>
namespace litehtml
{
    // A class that represents a pixel value as a float. It provides arithmetic and comparison operators for easy
    // manipulation of pixel values.
    class pixel_float_t
    {
        constexpr static float epsilon = 0.0001f;

        float m_value = 0.0f;

      public:
        pixel_float_t() = default;
        constexpr pixel_float_t(float value) :
            m_value(value)
        {
        }
        constexpr pixel_float_t(int value) :
            m_value(static_cast<float>(value))
        {
        }
        constexpr operator float() const
        {
            return m_value;
        }
        constexpr operator int() const
        {
            return static_cast<int>(m_value);
        }
        constexpr float value() const
        {
            return m_value;
        }

        // Assignment operators
        constexpr pixel_float_t& operator=(float value)
        {
            m_value = value;
            return *this;
        }
        constexpr pixel_float_t& operator=(int value)
        {
            m_value = static_cast<float>(value);
            return *this;
        }

        // += operators
        constexpr pixel_float_t& operator+=(pixel_float_t value)
        {
            m_value += value.m_value;
            return *this;
        }
        constexpr pixel_float_t& operator+=(float value)
        {
            m_value += value;
            return *this;
        }
        constexpr pixel_float_t& operator+=(int value)
        {
            m_value += static_cast<float>(value);
            return *this;
        }

        // -= operators
        constexpr pixel_float_t& operator-=(float value)
        {
            m_value -= value;
            return *this;
        }
        constexpr pixel_float_t& operator-=(int value)
        {
            m_value -= static_cast<float>(value);
            return *this;
        }
        constexpr pixel_float_t& operator-=(pixel_float_t value)
        {
            m_value -= value.m_value;
            return *this;
        }

        // *= operators
        constexpr pixel_float_t& operator*=(float value)
        {
            m_value *= value;
            return *this;
        }
        constexpr pixel_float_t& operator*=(int value)
        {
            m_value *= static_cast<float>(value);
            return *this;
        }
        constexpr pixel_float_t& operator*=(pixel_float_t value)
        {
            m_value *= value.m_value;
            return *this;
        }

        // /= operators
        constexpr pixel_float_t& operator/=(float value)
        {
            m_value /= value;
            return *this;
        }
        constexpr pixel_float_t& operator/=(int value)
        {
            m_value /= static_cast<float>(value);
            return *this;
        }
        constexpr pixel_float_t& operator/=(pixel_float_t value)
        {
            m_value /= value.m_value;
            return *this;
        }

        // Comparison operators
        bool operator==(pixel_float_t other) const
        {
            return std::abs(m_value - other.m_value) < epsilon;
        }
        bool operator!=(pixel_float_t other) const
        {
            return !(*this == other);
        }
        constexpr bool operator<(pixel_float_t other) const
        {
            return m_value < other.m_value && std::abs(m_value - other.m_value) >= epsilon;
        }
        constexpr bool operator>(pixel_float_t other) const
        {
            return m_value > other.m_value && std::abs(m_value - other.m_value) >= epsilon;
        }
        constexpr bool operator<=(pixel_float_t other) const
        {
            return *this < other || *this == other;
        }
        constexpr bool operator>=(pixel_float_t other) const
        {
            return *this > other || *this == other;
        }

        // Unary - operator
        constexpr pixel_float_t operator-() const
        {
            return {-m_value};
        }

        // + operators
        constexpr pixel_float_t operator+(pixel_float_t other) const
        {
            return {m_value + other.m_value};
        }
        constexpr pixel_float_t operator+(float other) const
        {
            return {m_value + other};
        }
        constexpr pixel_float_t operator+(int other) const
        {
            return {m_value + static_cast<float>(other)};
        }

        // - operators
        constexpr pixel_float_t operator-(float other) const
        {
            return {m_value - other};
        }
        constexpr pixel_float_t operator-(int other) const
        {
            return {m_value - static_cast<float>(other)};
        }
        constexpr pixel_float_t operator-(pixel_float_t other) const
        {
            return {m_value - other.m_value};
        }

        // * operators
        constexpr pixel_float_t operator*(float other) const
        {
            return {m_value * other};
        }
        constexpr pixel_float_t operator*(int other) const
        {
            return {m_value * static_cast<float>(other)};
        }
        constexpr pixel_float_t operator*(pixel_float_t value) const
        {
            return {m_value * value.m_value};
        }

        // / operators
        constexpr pixel_float_t operator/(float value) const
        {
            return {m_value / value};
        }
        constexpr pixel_float_t operator/(int value) const
        {
            return {m_value / static_cast<float>(value)};
        }
        constexpr pixel_float_t operator/(pixel_float_t value) const
        {
            return {m_value / value.m_value};
        }

        // Increment and Decrement operators
        constexpr pixel_float_t operator++()
        {
            ++m_value;
            return *this;
        }
        constexpr pixel_float_t operator++(int)
        {
            pixel_float_t temp = *this;
            ++(*this);
            return temp;
        }
        constexpr pixel_float_t operator--()
        {
            --m_value;
            return *this;
        }
        constexpr pixel_float_t operator--(int)
        {
            pixel_float_t temp = *this;
            --(*this);
            return temp;
        }
    };

    constexpr pixel_float_t operator""_px(long double val)
    {
        return {static_cast<float>(val)};
    }

    constexpr pixel_float_t operator""_px(unsigned long long val)
    {
        return {static_cast<float>(val)};
    }

    constexpr pixel_float_t operator*(float a, pixel_float_t b)
    {
        return {a * b.value()};
    }
    constexpr pixel_float_t operator*(int a, pixel_float_t b)
    {
        return {static_cast<float>(a) * b.value()};
    }
    constexpr pixel_float_t operator+(float a, pixel_float_t b)
    {
        return {a + b.value()};
    }
    constexpr pixel_float_t operator+(int a, pixel_float_t b)
    {
        return {static_cast<float>(a) + b.value()};
    }
    constexpr pixel_float_t operator-(float a, pixel_float_t b)
    {
        return {a - b.value()};
    }
    constexpr pixel_float_t operator-(int a, pixel_float_t b)
    {
        return {static_cast<float>(a) - b.value()};
    }
    constexpr pixel_float_t operator/(float a, pixel_float_t b)
    {
        return {a / b.value()};
    }
    constexpr pixel_float_t operator/(int a, pixel_float_t b)
    {
        return {static_cast<float>(a) / b.value()};
    }

    // Define pixel_t as an alias for pixel_float_t
    using pixel_t = pixel_float_t;

} // namespace litehtml
#endif // LITEHTML_PIXEL_TYPE_H
