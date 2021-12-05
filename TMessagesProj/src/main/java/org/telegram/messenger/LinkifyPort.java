package org.telegram.messenger;

import java.util.regex.Pattern;

public class LinkifyPort {

    private static String IANA_TOP_LEVEL_DOMAINS =
            "(?:"
                    + "(?:aaa|aarp|abb|abbott|abogado|academy|accenture|accountant|accountants|aco|active"
                    + "|actor|ads|adult|aeg|aero|afl|agency|aig|airforce|airtel|allfinanz|alsace|amica|amsterdam"
                    + "|android|apartments|app|apple|aquarelle|aramco|archi|army|arpa|arte|asia|associates"
                    + "|attorney|auction|audio|auto|autos|axa|azure|a[cdefgilmoqrstuwxz])"
                    + "|(?:band|bank|bar|barcelona|barclaycard|barclays|bargains|bauhaus|bayern|bbc|bbva"
                    + "|bcn|beats|beer|bentley|berlin|best|bet|bharti|bible|bid|bike|bing|bingo|bio|biz|black"
                    + "|blackfriday|bloomberg|blue|bms|bmw|bnl|bnpparibas|boats|bom|bond|boo|boots|boutique"
                    + "|bradesco|bridgestone|broadway|broker|brother|brussels|budapest|build|builders|business"
                    + "|buzz|bzh|b[abdefghijmnorstvwyz])"
                    + "|(?:cab|cafe|cal|camera|camp|cancerresearch|canon|capetown|capital|car|caravan|cards"
                    + "|care|career|careers|cars|cartier|casa|cash|casino|cat|catering|cba|cbn|ceb|center|ceo"
                    + "|cern|cfa|cfd|chanel|channel|chat|cheap|chloe|christmas|chrome|church|cipriani|cisco"
                    + "|citic|city|cityeats|claims|cleaning|click|clinic|clothing|cloud|club|clubmed|coach"
                    + "|codes|coffee|college|cologne|com|commbank|community|company|computer|comsec|condos"
                    + "|construction|consulting|contractors|cooking|cool|coop|corsica|country|coupons|courses"
                    + "|credit|creditcard|creditunion|cricket|crown|crs|cruises|csc|cuisinella|cymru|cyou|c[acdfghiklmnoruvwxyz])"
                    + "|(?:dabur|dad|dance|date|dating|datsun|day|dclk|deals|degree|delivery|dell|delta"
                    + "|democrat|dental|dentist|desi|design|dev|diamonds|diet|digital|direct|directory|discount"
                    + "|dnp|docs|dog|doha|domains|doosan|download|drive|durban|dvag|d[ejkmoz])"
                    + "|(?:earth|eat|edu|education|email|emerck|energy|engineer|engineering|enterprises"
                    + "|epson|equipment|erni|esq|estate|eurovision|eus|events|everbank|exchange|expert|exposed"
                    + "|express|e[cegrstu])"
                    + "|(?:fage|fail|fairwinds|faith|family|fan|fans|farm|fashion|feedback|ferrero|film"
                    + "|final|finance|financial|firmdale|fish|fishing|fit|fitness|flights|florist|flowers|flsmidth"
                    + "|fly|foo|football|forex|forsale|forum|foundation|frl|frogans|fund|furniture|futbol|fyi"
                    + "|f[ijkmor])"
                    + "|(?:gal|gallery|game|garden|gbiz|gdn|gea|gent|genting|ggee|gift|gifts|gives|giving"
                    + "|glass|gle|global|globo|gmail|gmo|gmx|gold|goldpoint|golf|goo|goog|google|gop|gov|grainger"
                    + "|graphics|gratis|green|gripe|group|gucci|guge|guide|guitars|guru|g[abdefghilmnpqrstuwy])"
                    + "|(?:hamburg|hangout|haus|healthcare|help|here|hermes|hiphop|hitachi|hiv|hockey|holdings"
                    + "|holiday|homedepot|homes|honda|horse|host|hosting|hoteles|hotmail|house|how|hsbc|hyundai"
                    + "|h[kmnrtu])"
                    + "|(?:ibm|icbc|ice|icu|ifm|iinet|immo|immobilien|industries|infiniti|info|ing|ink|institute"
                    + "|insure|int|international|investments|ipiranga|irish|ist|istanbul|itau|iwc|i[delmnoqrst])"
                    + "|(?:jaguar|java|jcb|jetzt|jewelry|jlc|jll|jobs|joburg|jprs|juegos|j[emop])"
                    + "|(?:kaufen|kddi|kia|kim|kinder|kitchen|kiwi|koeln|komatsu|krd|kred|kyoto|k[eghimnprwyz])"
                    + "|(?:lacaixa|lancaster|land|landrover|lasalle|lat|latrobe|law|lawyer|lds|lease|leclerc"
                    + "|legal|lexus|lgbt|liaison|lidl|life|lifestyle|lighting|limited|limo|linde|link|live"
                    + "|lixil|loan|loans|lol|london|lotte|lotto|love|ltd|ltda|lupin|luxe|luxury|l[abcikrstuvy])"
                    + "|(?:madrid|maif|maison|man|management|mango|market|marketing|markets|marriott|mba"
                    + "|media|meet|melbourne|meme|memorial|men|menu|meo|miami|microsoft|mil|mini|mma|mobi|moda"
                    + "|moe|moi|mom|monash|money|montblanc|mormon|mortgage|moscow|motorcycles|mov|movie|movistar"
                    + "|mtn|mtpc|mtr|museum|mutuelle|m[acdeghklmnopqrstuvwxyz])"
                    + "|(?:nadex|nagoya|name|navy|nec|net|netbank|network|neustar|new|news|nexus|ngo|nhk"
                    + "|nico|ninja|nissan|nokia|nra|nrw|ntt|nyc|n[acefgilopruz])"
                    + "|(?:obi|office|okinawa|omega|one|ong|onl|online|ooo|oracle|orange|org|organic|osaka"
                    + "|otsuka|ovh|om)"
                    + "|(?:page|panerai|paris|partners|parts|party|pet|pharmacy|philips|photo|photography"
                    + "|photos|physio|piaget|pics|pictet|pictures|ping|pink|pizza|place|play|playstation|plumbing"
                    + "|plus|pohl|poker|porn|post|praxi|press|pro|prod|productions|prof|properties|property"
                    + "|protection|pub|p[aefghklmnrstwy])"
                    + "|(?:qpon|quebec|qa)"
                    + "|(?:racing|realtor|realty|recipes|red|redstone|rehab|reise|reisen|reit|ren|rent|rentals"
                    + "|repair|report|republican|rest|restaurant|review|reviews|rich|ricoh|rio|rip|rocher|rocks"
                    + "|rodeo|rsvp|ruhr|run|rwe|ryukyu|r[eosuw])"
                    + "|(?:saarland|sakura|sale|samsung|sandvik|sandvikcoromant|sanofi|sap|sapo|sarl|saxo"
                    + "|sbs|sca|scb|schmidt|scholarships|school|schule|schwarz|science|scor|scot|seat|security"
                    + "|seek|sener|services|seven|sew|sex|sexy|shiksha|shoes|show|shriram|singles|site|ski"
                    + "|sky|skype|sncf|soccer|social|software|sohu|solar|solutions|sony|soy|space|spiegel|spreadbetting"
                    + "|srl|stada|starhub|statoil|stc|stcgroup|stockholm|studio|study|style|sucks|supplies"
                    + "|supply|support|surf|surgery|suzuki|swatch|swiss|sydney|systems|s[abcdeghijklmnortuvxyz])"
                    + "|(?:tab|taipei|tatamotors|tatar|tattoo|tax|taxi|team|tech|technology|tel|telefonica"
                    + "|temasek|tennis|thd|theater|theatre|tickets|tienda|tips|tires|tirol|today|tokyo|tools"
                    + "|top|toray|toshiba|tours|town|toyota|toys|trade|trading|training|travel|trust|tui|t[cdfghjklmnortvwz])"
                    + "|(?:ubs|university|uno|uol|u[agksyz])"
                    + "|(?:vacations|vana|vegas|ventures|versicherung|vet|viajes|video|villas|vin|virgin"
                    + "|vision|vista|vistaprint|viva|vlaanderen|vodka|vote|voting|voto|voyage|v[aceginu])"
                    + "|(?:wales|walter|wang|watch|webcam|website|wed|wedding|weir|whoswho|wien|wiki|williamhill"
                    + "|win|windows|wine|wme|work|works|world|wtc|wtf|w[fs])"
                    + "|(?:\u03b5\u03bb|\u0431\u0435\u043b|\u0434\u0435\u0442\u0438|\u043a\u043e\u043c|\u043c\u043a\u0434"
                    + "|\u043c\u043e\u043d|\u043c\u043e\u0441\u043a\u0432\u0430|\u043e\u043d\u043b\u0430\u0439\u043d"
                    + "|\u043e\u0440\u0433|\u0440\u0443\u0441|\u0440\u0444|\u0441\u0430\u0439\u0442|\u0441\u0440\u0431"
                    + "|\u0443\u043a\u0440|\u049b\u0430\u0437|\u0570\u0561\u0575|\u05e7\u05d5\u05dd|\u0627\u0631\u0627\u0645\u0643\u0648"
                    + "|\u0627\u0644\u0627\u0631\u062f\u0646|\u0627\u0644\u062c\u0632\u0627\u0626\u0631|\u0627\u0644\u0633\u0639\u0648\u062f\u064a\u0629"
                    + "|\u0627\u0644\u0645\u063a\u0631\u0628|\u0627\u0645\u0627\u0631\u0627\u062a|\u0627\u06cc\u0631\u0627\u0646"
                    + "|\u0628\u0627\u0632\u0627\u0631|\u0628\u06be\u0627\u0631\u062a|\u062a\u0648\u0646\u0633"
                    + "|\u0633\u0648\u062f\u0627\u0646|\u0633\u0648\u0631\u064a\u0629|\u0634\u0628\u0643\u0629"
                    + "|\u0639\u0631\u0627\u0642|\u0639\u0645\u0627\u0646|\u0641\u0644\u0633\u0637\u064a\u0646"
                    + "|\u0642\u0637\u0631|\u0643\u0648\u0645|\u0645\u0635\u0631|\u0645\u0644\u064a\u0633\u064a\u0627"
                    + "|\u0645\u0648\u0642\u0639|\u0915\u0949\u092e|\u0928\u0947\u091f|\u092d\u093e\u0930\u0924"
                    + "|\u0938\u0902\u0917\u0920\u0928|\u09ad\u09be\u09b0\u09a4|\u0a2d\u0a3e\u0a30\u0a24|\u0aad\u0abe\u0ab0\u0aa4"
                    + "|\u0b87\u0ba8\u0bcd\u0ba4\u0bbf\u0baf\u0bbe|\u0b87\u0bb2\u0b99\u0bcd\u0b95\u0bc8|\u0b9a\u0bbf\u0b99\u0bcd\u0b95\u0baa\u0bcd\u0baa\u0bc2\u0bb0\u0bcd"
                    + "|\u0c2d\u0c3e\u0c30\u0c24\u0c4d|\u0dbd\u0d82\u0d9a\u0dcf|\u0e04\u0e2d\u0e21|\u0e44\u0e17\u0e22"
                    + "|\u10d2\u10d4|\u307f\u3093\u306a|\u30b0\u30fc\u30b0\u30eb|\u30b3\u30e0|\u4e16\u754c"
                    + "|\u4e2d\u4fe1|\u4e2d\u56fd|\u4e2d\u570b|\u4e2d\u6587\u7f51|\u4f01\u4e1a|\u4f5b\u5c71"
                    + "|\u4fe1\u606f|\u5065\u5eb7|\u516b\u5366|\u516c\u53f8|\u516c\u76ca|\u53f0\u6e7e|\u53f0\u7063"
                    + "|\u5546\u57ce|\u5546\u5e97|\u5546\u6807|\u5728\u7ebf|\u5927\u62ff|\u5a31\u4e50|\u5de5\u884c"
                    + "|\u5e7f\u4e1c|\u6148\u5584|\u6211\u7231\u4f60|\u624b\u673a|\u653f\u52a1|\u653f\u5e9c"
                    + "|\u65b0\u52a0\u5761|\u65b0\u95fb|\u65f6\u5c1a|\u673a\u6784|\u6de1\u9a6c\u9521|\u6e38\u620f"
                    + "|\u70b9\u770b|\u79fb\u52a8|\u7ec4\u7ec7\u673a\u6784|\u7f51\u5740|\u7f51\u5e97|\u7f51\u7edc"
                    + "|\u8c37\u6b4c|\u96c6\u56e2|\u98de\u5229\u6d66|\u9910\u5385|\u9999\u6e2f|\ub2f7\ub137"
                    + "|\ub2f7\ucef4|\uc0bc\uc131|\ud55c\uad6d|xbox"
                    + "|xerox|xin|xn\\-\\-11b4c3d|xn\\-\\-1qqw23a|xn\\-\\-30rr7y|xn\\-\\-3bst00m|xn\\-\\-3ds443g"
                    + "|xn\\-\\-3e0b707e|xn\\-\\-3pxu8k|xn\\-\\-42c2d9a|xn\\-\\-45brj9c|xn\\-\\-45q11c|xn\\-\\-4gbrim"
                    + "|xn\\-\\-55qw42g|xn\\-\\-55qx5d|xn\\-\\-6frz82g|xn\\-\\-6qq986b3xl|xn\\-\\-80adxhks"
                    + "|xn\\-\\-80ao21a|xn\\-\\-80asehdb|xn\\-\\-80aswg|xn\\-\\-90a3ac|xn\\-\\-90ais|xn\\-\\-9dbq2a"
                    + "|xn\\-\\-9et52u|xn\\-\\-b4w605ferd|xn\\-\\-c1avg|xn\\-\\-c2br7g|xn\\-\\-cg4bki|xn\\-\\-clchc0ea0b2g2a9gcd"
                    + "|xn\\-\\-czr694b|xn\\-\\-czrs0t|xn\\-\\-czru2d|xn\\-\\-d1acj3b|xn\\-\\-d1alf|xn\\-\\-efvy88h"
                    + "|xn\\-\\-estv75g|xn\\-\\-fhbei|xn\\-\\-fiq228c5hs|xn\\-\\-fiq64b|xn\\-\\-fiqs8s|xn\\-\\-fiqz9s"
                    + "|xn\\-\\-fjq720a|xn\\-\\-flw351e|xn\\-\\-fpcrj9c3d|xn\\-\\-fzc2c9e2c|xn\\-\\-gecrj9c"
                    + "|xn\\-\\-h2brj9c|xn\\-\\-hxt814e|xn\\-\\-i1b6b1a6a2e|xn\\-\\-imr513n|xn\\-\\-io0a7i"
                    + "|xn\\-\\-j1aef|xn\\-\\-j1amh|xn\\-\\-j6w193g|xn\\-\\-kcrx77d1x4a|xn\\-\\-kprw13d|xn\\-\\-kpry57d"
                    + "|xn\\-\\-kput3i|xn\\-\\-l1acc|xn\\-\\-lgbbat1ad8j|xn\\-\\-mgb9awbf|xn\\-\\-mgba3a3ejt"
                    + "|xn\\-\\-mgba3a4f16a|xn\\-\\-mgbaam7a8h|xn\\-\\-mgbab2bd|xn\\-\\-mgbayh7gpa|xn\\-\\-mgbbh1a71e"
                    + "|xn\\-\\-mgbc0a9azcg|xn\\-\\-mgberp4a5d4ar|xn\\-\\-mgbpl2fh|xn\\-\\-mgbtx2b|xn\\-\\-mgbx4cd0ab"
                    + "|xn\\-\\-mk1bu44c|xn\\-\\-mxtq1m|xn\\-\\-ngbc5azd|xn\\-\\-node|xn\\-\\-nqv7f|xn\\-\\-nqv7fs00ema"
                    + "|xn\\-\\-nyqy26a|xn\\-\\-o3cw4h|xn\\-\\-ogbpf8fl|xn\\-\\-p1acf|xn\\-\\-p1ai|xn\\-\\-pgbs0dh"
                    + "|xn\\-\\-pssy2u|xn\\-\\-q9jyb4c|xn\\-\\-qcka1pmc|xn\\-\\-qxam|xn\\-\\-rhqv96g|xn\\-\\-s9brj9c"
                    + "|xn\\-\\-ses554g|xn\\-\\-t60b56a|xn\\-\\-tckwe|xn\\-\\-unup4y|xn\\-\\-vermgensberater\\-ctb"
                    + "|xn\\-\\-vermgensberatung\\-pwb|xn\\-\\-vhquv|xn\\-\\-vuq861b|xn\\-\\-wgbh1c|xn\\-\\-wgbl6a"
                    + "|xn\\-\\-xhq521b|xn\\-\\-xkc2al3hye2a|xn\\-\\-xkc2dl3a5ee0h|xn\\-\\-y9a3aq|xn\\-\\-yfro4i67o"
                    + "|xn\\-\\-ygbi2ammx|xn\\-\\-zfr164b|xperia|xxx|xyz)"
                    + "|(?:yachts|yamaxun|yandex|yodobashi|yoga|yokohama|youtube|y[et])"
                    + "|(?:zara|zip|zone|zuerich|z[amw]))";
    private static final String UCS_CHAR = "[" +
            "\u00A0-\uD7FF" +
            "\uF900-\uFDCF" +
            "\uFDF0-\uFFEF" +
            "\uD800\uDC00-\uD83F\uDFFD" +
            "\uD840\uDC00-\uD87F\uDFFD" +
            "\uD880\uDC00-\uD8BF\uDFFD" +
            "\uD8C0\uDC00-\uD8FF\uDFFD" +
            "\uD900\uDC00-\uD93F\uDFFD" +
            "\uD940\uDC00-\uD97F\uDFFD" +
            "\uD980\uDC00-\uD9BF\uDFFD" +
            "\uD9C0\uDC00-\uD9FF\uDFFD" +
            "\uDA00\uDC00-\uDA3F\uDFFD" +
            "\uDA40\uDC00-\uDA7F\uDFFD" +
            "\uDA80\uDC00-\uDABF\uDFFD" +
            "\uDAC0\uDC00-\uDAFF\uDFFD" +
            "\uDB00\uDC00-\uDB3F\uDFFD" +
            "\uDB44\uDC00-\uDB7F\uDFFD" +
            "&&[^\u00A0[\u2000-\u200A]\u2028\u2029\u202F\u3000]]";
    private static final String IP_ADDRESS_STRING =
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                    + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                    + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                    + "|[1-9][0-9]|[0-9]))";
    private static final String TLD_CHAR = "a-zA-Z" + UCS_CHAR;
    private static final String PUNYCODE_TLD = "xn\\-\\-[\\w\\-]{0,58}\\w";
    private static final String LABEL_CHAR = "a-zA-Z0-9" + UCS_CHAR;
    private static final String IRI_LABEL = "[" + LABEL_CHAR + "](?:[" + LABEL_CHAR + "_\\-]{0,61}[" + LABEL_CHAR + "]){0,1}";
    private static String STRICT_TLD = "(?:" + IANA_TOP_LEVEL_DOMAINS + "|" + PUNYCODE_TLD + ")";
    private static final String STRICT_HOST_NAME = "(?:(?:" + IRI_LABEL + "\\.)+" + STRICT_TLD + ")";
    private static final String STRICT_DOMAIN_NAME = "(?:" + STRICT_HOST_NAME + "|" + IP_ADDRESS_STRING + ")";
    private static final String TLD = "(" + PUNYCODE_TLD + "|" + "[" + TLD_CHAR + "]{2,63}" + ")";
    private static final String HOST_NAME = "(" + IRI_LABEL + "\\.)+" + TLD;
    private static final String DOMAIN_NAME_STR = "(" + HOST_NAME + "|" + IP_ADDRESS_STRING + ")";
    private static final Pattern DOMAIN_NAME = Pattern.compile(DOMAIN_NAME_STR);
    private static final String PROTOCOL = "(?i:http|https|ton|tg)://";
    private static final String WORD_BOUNDARY = "(?:\\b|$|^)";
    private static final String USER_INFO = "(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
            + "\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
            + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@";
    private static final String PORT_NUMBER = "\\:\\d{1,5}";
    private static final String PATH_AND_QUERY = "[/\\?](?:(?:[" + LABEL_CHAR + ";/\\?:@&=#~" + "\\-\\.\\+!\\*'\\(\\),_\\$])|(?:%[a-fA-F0-9]{2}))*";
    private static final String RELAXED_DOMAIN_NAME = "(?:" + "(?:" + IRI_LABEL + "(?:\\.(?=\\S))" + "?)+" + "|" + IP_ADDRESS_STRING + ")";

    private static final String WEB_URL_WITHOUT_PROTOCOL = "("
            + WORD_BOUNDARY
            + "(?<!:\\/\\/)"
            + "("
            + "(?:" + STRICT_DOMAIN_NAME + ")"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(?:" + PATH_AND_QUERY + ")?"
            + WORD_BOUNDARY
            + ")";

    private static final String WEB_URL_WITH_PROTOCOL = "("
            + WORD_BOUNDARY
            + "(?:"
            + "(?:" + PROTOCOL + "(?:" + USER_INFO + ")?" + ")"
            + "(?:" + RELAXED_DOMAIN_NAME + ")?"
            + "(?:" + PORT_NUMBER + ")?"
            + ")"
            + "(?:" + PATH_AND_QUERY + ")?"
            + WORD_BOUNDARY
            + ")";
    public static Pattern WEB_URL = null;

    static {
        try {
            WEB_URL = Pattern.compile("(" + WEB_URL_WITH_PROTOCOL + "|" + WEB_URL_WITHOUT_PROTOCOL + ")");
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
