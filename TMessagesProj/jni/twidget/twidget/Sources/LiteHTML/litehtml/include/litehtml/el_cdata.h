#ifndef LITEHTML_EL_CDATA_H
#define LITEHTML_EL_CDATA_H

#include "element.h"

namespace litehtml
{
    class el_cdata : public element
    {
        std::string m_text;

      public:
        explicit el_cdata(const std::shared_ptr<document>& doc);

        void get_text(std::string& text) const override;
        void set_data(const char* data) override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_CDATA_H
