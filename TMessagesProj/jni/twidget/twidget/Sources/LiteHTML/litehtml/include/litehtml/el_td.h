#ifndef LITEHTML_EL_TD_H
#define LITEHTML_EL_TD_H

#include "html_tag.h"

namespace litehtml
{
    class el_td : public html_tag
    {
      public:
        explicit el_td(const std::shared_ptr<litehtml::document>& doc);

        void parse_attributes() override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_TD_H
