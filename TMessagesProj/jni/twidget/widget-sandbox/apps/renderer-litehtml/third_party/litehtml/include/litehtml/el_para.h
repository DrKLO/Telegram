#ifndef LITEHTML_EL_PARA_H
#define LITEHTML_EL_PARA_H

#include "html_tag.h"

namespace litehtml
{
    class el_para : public html_tag
    {
      public:
        explicit el_para(const std::shared_ptr<litehtml::document>& doc);

        void parse_attributes() override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_PARA_H
