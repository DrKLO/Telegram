#ifndef LH_INTERNAL_H
#define LH_INTERNAL_H
// internal.h should not be included in header files
// internal.h should be included after all other headers in a source file

namespace litehtml
{

    template <class T, class TT> bool operator/(const T& x, const TT& xx)
    {
        return contains(xx, x);
    }
// a in b if b contains a
#define in /

    /* Limitations of overloaded operators compared to regular function calls:
     * 1. at least one operand must be a class, so cannot just write `ch in "abc"`
     *    (possible solution: ch in "abc"_s)
     * 2. operand cannot be initializer list (exception: assignment ops), so cannot just write `ch in {'a','b','c'}`
     *    (possible solution: ch in ${'a','b','c'})
     */

} // namespace litehtml

#endif // LH_INTERNAL_H
