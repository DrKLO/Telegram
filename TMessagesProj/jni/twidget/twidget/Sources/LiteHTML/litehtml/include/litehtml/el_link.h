#ifndef LITEHTML_EL_LINK_H
#define LITEHTML_EL_LINK_H

#include "html_tag.h"

namespace litehtml
{
    class el_link : public html_tag
    {
      public:
        explicit el_link(const std::shared_ptr<litehtml::document>& doc);

      protected:
        void parse_attributes() override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_LINK_H
