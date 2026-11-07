#ifndef LITEHTML_EL_TITLE_H
#define LITEHTML_EL_TITLE_H

#include "html_tag.h"

namespace litehtml
{
    class el_title : public html_tag
    {
      public:
        explicit el_title(const std::shared_ptr<litehtml::document>& doc);

      protected:
        void parse_attributes() override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_TITLE_H
