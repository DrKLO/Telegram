#ifndef LITEHTML_EL_SPACE_H
#define LITEHTML_EL_SPACE_H

#include "el_text.h"

namespace litehtml
{
    class el_space : public el_text
    {
      public:
        el_space(const char* text, const std::shared_ptr<document>& doc);

        bool is_white_space() const override;
        bool is_break() const override;
        bool is_space() const override;

        std::string dump_get_name() override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_SPACE_H
