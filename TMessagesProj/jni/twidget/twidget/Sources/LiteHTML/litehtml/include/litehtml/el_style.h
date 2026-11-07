#ifndef LITEHTML_EL_STYLE_H
#define LITEHTML_EL_STYLE_H

#include "element.h"

namespace litehtml
{
    class el_style : public element
    {
        elements_list m_children;

      public:
        explicit el_style(const std::shared_ptr<document>& doc);

        void        parse_attributes() override;
        bool        appendChild(const ptr& el) override;
        void        compute_styles(bool recursive) override;
        string_id   tag() const override;
        const char* get_tagName() const override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_STYLE_H
