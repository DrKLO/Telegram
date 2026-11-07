#ifndef LITEHTML_EL_TEXT_H
#define LITEHTML_EL_TEXT_H

#include "element.h"
#include "document.h"

namespace litehtml
{
    class text_breaker;
    class el_text : public element
    {
      protected:
        std::string m_text;
        std::string m_transformed_text;
        size        m_size;
        bool        m_use_transformed;
        bool        m_draw_spaces;
        uint_ptr    m_measured_font = 0;
        int         m_measured_transform = -1;
        std::shared_ptr<text_breaker> m_breaker;
        mutable int8_t m_strong_direction = 2;  // 2: not looked at yet

      public:
        el_text(const char* text, const document::ptr& doc);

        void get_text(std::string& text) const override;
        void set_text(const std::string& text);
        void compute_styles(bool recursive) override;
        const std::string& display_text() const { return m_use_transformed ? m_transformed_text : m_text; }
        std::shared_ptr<text_breaker> breaker();
        /// The direction of the text's first strong character: 1 right to left, -1 left to right, 0 none
        /// (digits, punctuation, spaces, symbols).
        int strong_direction() const;
        bool is_text() const override
        {
            return true;
        }

        void        draw(uint_ptr hdc, pixel_t x, pixel_t y, const position* clip,
                         const std::shared_ptr<render_item>& ri) override;
        std::string dump_get_name() override;

        std::vector<std::tuple<std::string, std::string>> dump_get_attrs() override;

      protected:
        void get_content_size(size& sz, pixel_t max_width) override;
    };
} // namespace litehtml

#endif // LITEHTML_EL_TEXT_H
