#ifndef LITEHTML_EL_BEFORE_AFTER_H
#define LITEHTML_EL_BEFORE_AFTER_H

#include "html_tag.h"

namespace litehtml
{
    class el_before_after_base : public html_tag
    {
      public:
        el_before_after_base(const std::shared_ptr<document>& doc, bool before);

        void add_style(const style& style) override;

      private:
        /// `escapes`: the text is as written, with CSS escapes still in it (not a string token's value).
        void               add_text(const std::string& txt, bool escapes = true);
        void               add_function(const std::string& fnc, const std::string& params);
        void               add_image(const std::string& url);
        /// open-quote / close-quote: a mark from the inherited `quotes`, the pair chosen by how many <q> enclose this one.
        void               add_quote(bool open);
        static std::string convert_escape(const char* txt);
    };

    class el_before : public el_before_after_base
    {
      public:
        explicit el_before(const std::shared_ptr<document>& doc) :
            el_before_after_base(doc, true)
        {
        }
    };

    class el_after : public el_before_after_base
    {
      public:
        explicit el_after(const std::shared_ptr<document>& doc) :
            el_before_after_base(doc, false)
        {
        }
    };
} // namespace litehtml

#endif // LITEHTML_EL_BEFORE_AFTER_H
