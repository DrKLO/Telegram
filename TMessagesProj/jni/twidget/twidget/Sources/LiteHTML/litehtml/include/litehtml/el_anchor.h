#ifndef LITEHTML_EL_ANCHOR_H
#define LITEHTML_EL_ANCHOR_H

#include "html_tag.h"

namespace litehtml
{
    class el_anchor : public html_tag
    {
      public:
        explicit el_anchor(const std::shared_ptr<litehtml::document>& doc);

        void on_click() override;
        void apply_stylesheet(const litehtml::css& stylesheet) override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_ANCHOR_H
