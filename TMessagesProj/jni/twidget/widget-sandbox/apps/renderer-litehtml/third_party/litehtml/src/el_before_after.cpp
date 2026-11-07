#include "html.h"
#include "el_before_after.h"
#include "el_text.h"
#include "el_space.h"
#include "el_image.h"
#include "utf8_strings.h"

litehtml::el_before_after_base::el_before_after_base(const std::shared_ptr<document>& doc, bool before) :
    html_tag(doc)
{
    m_tag = before ? __tag_before_ : __tag_after_;
}

void litehtml::el_before_after_base::add_style(const style& style)
{
    html_tag::add_style(style);

    auto children = m_children;
    m_children.clear();

    const auto& content_property = style.get_property(_content_);
    if(content_property.is<std::string>() && !content_property.get<std::string>().empty())
    {
        const auto& str = content_property.get<std::string>();
        auto        idx = css_values(content_property_strings).value_index(str);
        if(!idx.has_value())
        {
            std::string            fnc;
            std::string::size_type i = 0;
            while(i < str.length() && i != std::string::npos)
            {
                if(str.at(i) == '"' || str.at(i) == '\'')
                {
                    auto chr = str.at(i);
                    fnc.clear();
                    i++;
                    std::string::size_type pos = str.find(chr, i);
                    std::string            txt;
                    if(pos == std::string::npos)
                    {
                        txt = str.substr(i);
                        i   = std::string::npos;
                    } else
                    {
                        txt = str.substr(i, pos - i);
                        i   = pos + 1;
                    }
                    add_text(txt);
                } else if(str.at(i) == '(')
                {
                    i++;
                    litehtml::trim(fnc);
                    litehtml::lcase(fnc);
                    std::string::size_type pos = str.find(')', i);
                    std::string            params;
                    if(pos == std::string::npos)
                    {
                        params = str.substr(i);
                        i      = std::string::npos;
                    } else
                    {
                        params = str.substr(i, pos - i);
                        i      = pos + 1;
                    }
                    add_function(fnc, params);
                    fnc.clear();
                } else
                {
                    fnc += str.at(i);
                    i++;
                }
            }
        }
    }

    if(m_children.empty())
    {
        m_children = children;
    }
}

void litehtml::el_before_after_base::add_text(const std::string& txt)
{
    std::string word;
    std::string esc;

    for(auto chr : txt)
    {
        if(chr == '\\' || (!esc.empty() && esc.length() < 5 &&
                           ((chr >= '0' && chr <= '9') || (chr >= 'A' && chr <= 'Z') || (chr >= 'a' && chr <= 'z'))))
        {
            if(!esc.empty() && chr == '\\')
            {
                word += convert_escape(esc.c_str() + 1);
                esc.clear();
            }
            esc += chr;
        } else
        {
            if(!esc.empty())
            {
                word += convert_escape(esc.c_str() + 1);
                esc.clear();
            }
            if(isspace(static_cast<unsigned char>(chr)))
            {
                if(!word.empty())
                {
                    element::ptr el = std::make_shared<el_text>(word.c_str(), get_document());
                    appendChild(el);
                    word.clear();
                }
                word            += chr;
                element::ptr el  = std::make_shared<el_space>(word.c_str(), get_document());
                appendChild(el);
                word.clear();
            } else
            {
                word += chr;
            }
        }
    }

    if(!esc.empty())
    {
        word += convert_escape(esc.c_str() + 1);
    }
    if(!word.empty())
    {
        element::ptr el = std::make_shared<el_text>(word.c_str(), get_document());
        appendChild(el);
        word.clear();
    }
}

void litehtml::el_before_after_base::add_function(const std::string& fnc, const std::string& params)
{
    constexpr auto content_function_strings = split_css_values<4>("attr;counter;counters;url");

    auto idx = css_values(content_function_strings).value_index(fnc);
    if(!idx.has_value())
    {
        return;
    }
    switch(idx.value())
    {
    // attr
    case 0:
        {
            std::string p_name = params;
            trim(p_name);
            lcase(p_name);
            element::ptr el_parent = parent();
            if(el_parent)
            {
                const char* attr_value = el_parent->get_attr(p_name.c_str());
                if(attr_value)
                {
                    add_text(attr_value);
                }
            }
        }
        break;
    // counter
    case 1:
        add_text(get_counter_value(params));
        break;
    // counters
    case 2:
        {
            string_vector tokens;
            split_string(params, tokens, ",");
            for(auto& str : tokens)
            {
                trim(str);
            }
            add_text(get_counters_value(tokens));
        }
        break;
    // url
    case 3:
        {
            std::string p_url = params;
            trim(p_url);
            if(!p_url.empty())
            {
                if(p_url.at(0) == '\'' || p_url.at(0) == '\"')
                {
                    p_url.erase(0, 1);
                }
            }
            if(!p_url.empty())
            {
                if(p_url.at(p_url.length() - 1) == '\'' || p_url.at(p_url.length() - 1) == '\"')
                {
                    p_url.erase(p_url.length() - 1, 1);
                }
            }
            if(!p_url.empty())
            {
                element::ptr el = std::make_shared<el_image>(get_document());
                el->set_attr("src", p_url.c_str());
                el->set_attr("style", "display:inline-block");
                el->set_tagName("img");
                appendChild(el);
                el->parse_attributes();
            }
        }
        break;
    }
}

std::string litehtml::el_before_after_base::convert_escape(const char* txt)
{
    char*    str_end;
    char32_t u_str[2];
    u_str[0] = static_cast<char32_t>(strtol(txt, &str_end, 16));
    u_str[1] = 0;
    return litehtml_from_utf32(u_str).c_str();
}
