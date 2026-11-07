#ifndef LITEHTML_HTML_MICROSYNTAXES_H
#define LITEHTML_HTML_MICROSYNTAXES_H

#include <string>
namespace litehtml
{

    bool html_parse_integer(const std::string& str, int& val);
    bool html_parse_non_negative_integer(const std::string& str, int& val);

    enum html_dimension_type
    {
        html_length,
        html_percentage
    };

    bool html_parse_dimension_value(const std::string& str, float& val, html_dimension_type& type);
    bool html_parse_nonzero_dimension_value(const std::string& str, float& val, html_dimension_type& type);

} // namespace litehtml

#endif // LITEHTML_HTML_MICROSYNTAXES_H
