#ifndef LITEHTML_EL_TR_H
#define LITEHTML_EL_TR_H

#include "html_tag.h"

namespace litehtml
{
    class el_tr : public html_tag
    {
      public:
        explicit el_tr(const std::shared_ptr<litehtml::document>& doc);

        void parse_attributes() override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_TR_H
