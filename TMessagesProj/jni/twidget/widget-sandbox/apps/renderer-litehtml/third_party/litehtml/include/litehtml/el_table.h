#ifndef LITEHTML_EL_TABLE_H
#define LITEHTML_EL_TABLE_H

#include "html_tag.h"

namespace litehtml
{
    class el_table : public html_tag
    {
      public:
        explicit el_table(const std::shared_ptr<litehtml::document>& doc);

        bool appendChild(const litehtml::element::ptr& el) override;
        void parse_attributes() override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_TABLE_H
