#ifndef LITEHTML_ENCODINGS_H
#define LITEHTML_ENCODINGS_H

#include <string>
namespace litehtml
{

    // https://encoding.spec.whatwg.org/#names-and-labels
    enum class encoding
    {
        null, // indicates error or absence of encoding
        utf_8,

        // Legacy single-byte encodings; must be in sync with single_byte_indexes
        ibm866,
        iso_8859_2,
        iso_8859_3,
        iso_8859_4,
        iso_8859_5,
        iso_8859_6,
        iso_8859_7,
        iso_8859_8,
        iso_8859_8_i,
        iso_8859_10,
        iso_8859_13,
        iso_8859_14,
        iso_8859_15,
        iso_8859_16,
        koi8_r,
        koi8_u,
        macintosh,
        windows_874,
        windows_1250,
        windows_1251,
        windows_1252,
        windows_1253,
        windows_1254,
        windows_1255,
        windows_1256,
        windows_1257,
        windows_1258,
        x_mac_cyrillic,

        // Legacy multi-byte East Asian encodings
        gbk,
        gb18030,
        big5,
        euc_jp,
        iso_2022_jp,
        shift_jis,
        euc_kr,

        // Legacy miscellaneous encodings
        replacement,
        utf_16be,
        utf_16le,
        x_user_defined
    };

    // https://html.spec.whatwg.org/multipage/parsing.html#concept-encoding-confidence
    enum class confidence // encoding confidence
    {
        tentative,
        certain,
        // irrelevant // not used here
    };

    // Used as argument for document::createFromString, parse_html and encoding_sniffing_algorithm.
    struct estring : std::string // string with encoding
    {
        litehtml::encoding   encoding;
        litehtml::confidence confidence;

        estring(const std::string& str, litehtml::encoding encoding = encoding::null,
                litehtml::confidence confidence = confidence::certain) :
            std::string(str),
            encoding(encoding),
            confidence(confidence)
        {
        }

        estring(const char* str) :
            std::string(str),
            encoding(encoding::null),
            confidence(confidence::certain)
        {
        }
    };

    encoding bom_sniff(const std::string& str);
    void     encoding_sniffing_algorithm(estring& str);

    encoding get_encoding(std::string label);
    encoding extract_encoding_from_meta_element(std::string str);

    void        decode(std::string input, encoding coding, std::string& output);
    std::string decode(std::string input, encoding coding);

} // namespace litehtml

#endif // LITEHTML_ENCODINGS_H
