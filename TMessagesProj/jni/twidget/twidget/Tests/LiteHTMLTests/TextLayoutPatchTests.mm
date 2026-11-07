#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <functional>
#include <random>
#include <string>
#include <vector>

@interface TextLayoutPatchTests : XCTestCase
@end

@implementation TextLayoutPatchTests

static NSString *const PaintSheet = @"body{margin:4px;font:12px -apple-system}.up{color:#34c759}.down{color:#ff3b30}.hot{background:#fee;border-color:#f00}"
                                     ".glow{opacity:.6;box-shadow:0 0 3px #000}.wide{padding:0 6px;font-size:15px}.bold{font-weight:bold}"
                                     ".tag::before{content:\"#\"}.gone{display:none}.flt{float:left;width:30%}.abs{position:absolute;right:2px;top:2px}"
                                     ".rel{position:relative;left:3px}.ib{display:inline-block;min-width:20px}.pre{white-space:pre}.caps{text-transform:uppercase}"
                                     "table{border-collapse:collapse;width:100%}td{padding:1px 3px;text-align:right;font-variant-numeric:tabular-nums}"
                                     ".fixed{table-layout:fixed}.row{display:flex;gap:4px}.row>*{flex:1 1 auto}.box{border:1px solid #888;margin:2px}";
static NSString *const IdSheet = @"#c3{color:#00f}[id=\"c5\"]{font-size:16px}.box:not(#c7) .up{background:#eef}";
static NSString *const ReachSheet = @".hot .up{font-size:18px}.up+.down{margin-left:4px}";

struct TextNode
{
    std::string              tag;
    int                      id = 0;
    std::string              dom_id;
    std::vector<std::string> classes;
    std::string              style;
    std::string              text;
    std::vector<TextNode>    children;
};

static void Escape(const std::string &text, std::string &out)
{
    for(char c : text)
    {
        if(c == '<')
            out += "&lt;";
        else if(c == '&')
            out += "&amp;";
        else
            out += c;
    }
}

static void Write(const TextNode &node, std::string &out)
{
    if(node.tag.empty())
    {
        Escape(node.text, out);
        return;
    }
    out += "<" + node.tag + " data-lh-id=\"" + std::to_string(node.id) + "\"";
    if(!node.dom_id.empty())
        out += " id=\"" + node.dom_id + "\"";
    if(!node.classes.empty())
    {
        out += " class=\"";
        for(size_t i = 0; i < node.classes.size(); i++)
            out += (i ? " " : "") + node.classes[i];
        out += "\"";
    }
    if(!node.style.empty())
        out += " style=\"" + node.style + "\"";
    out += ">";
    for(const auto &child : node.children)
        Write(child, out);
    out += "</" + node.tag + ">";
}

static void Gather(TextNode &node, std::vector<TextNode *> &texts, std::vector<TextNode *> &elements)
{
    if(node.tag.empty())
        texts.push_back(&node);
    else
        elements.push_back(&node);
    for(auto &child : node.children)
        Gather(child, texts, elements);
}

static NSData *Image(LHDocument *document, CGFloat width)
{
    CGSize     size  = [document layoutWithMaxWidth:width preferredWidth:width];
    CGImageRef image = [document copyImageWithMaxWidth:width rect:CGRectMake(0, 0, width, MAX(1, ceil(size.height))) scale:2];
    if(!image)
        return [NSData data];
    NSData *data = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return data;
}

static NSString *Page(NSString *css, const std::string &body)
{
    return [NSString stringWithFormat:@"<!DOCTYPE html><html data-lh-id=\"1\"><head data-lh-id=\"2\"><style data-lh-id=\"3\">%@</style></head>"
                                       "<body data-lh-id=\"4\">%@</body></html>",
                                       css, @(body.c_str())];
}

- (void)expect:(LHDocument *)patched like:(LHDocument *)fresh width:(CGFloat)width label:(NSString *)label
{
    NSData *a = Image(patched, width), *b = Image(fresh, width);
    XCTAssertEqualObjects(patched.layoutSignature, fresh.layoutSignature, @"bounds %@", label);
    XCTAssertEqual(patched.size.height, fresh.size.height, @"height %@", label);
    XCTAssertEqualObjects(a, b, @"pixels %@", label);
    XCTAssertEqualObjects([patched parentsByAttribute:@"data-lh-id"], [fresh parentsByAttribute:@"data-lh-id"], @"tree %@", label);
}

- (void)testRelayoutOfAKeptTreeMatchesAFreshLayout
{
    NSArray<NSString *> *bodies = @[
        @"<p data-lh-id=\"5\"><b data-lh-id=\"6\" style=\"position:relative;left:3px;top:2px\">shifted</b> text</p>",
        @"<p data-lh-id=\"5\">a <span data-lh-id=\"6\" style=\"position:relative;right:4px;padding:0 6px\">long run of words <i data-lh-id=\"7\" style=\"position:relative;top:-3px\">nested</i> that wraps over several lines</span> end</p>",
        @"<div data-lh-id=\"5\"><span data-lh-id=\"6\" class=\"rel wide\">split <p data-lh-id=\"7\">block</p> inline</span></div>",
    ];
    for(NSString *body in bodies)
    {
        NSString   *html  = Page(PaintSheet, body.UTF8String);
        LHDocument *again = [[LHDocument alloc] initWithHTML:html css:nil];
        [again layoutWithMaxWidth:120 preferredWidth:120];
        [again layoutWithMaxWidth:300 preferredWidth:300];
        [again layoutWithMaxWidth:200 preferredWidth:200];
        LHDocument *fresh = [[LHDocument alloc] initWithHTML:html css:nil];
        [fresh layoutWithMaxWidth:200 preferredWidth:200];
        XCTAssertEqualObjects(again.layoutSignature, fresh.layoutSignature, @"%@", body);
        XCTAssertEqualObjects(Image(again, 200), Image(fresh, 200), @"%@", body);
    }
}

- (void)testTickerTextAndColorFramesMatchAFreshLayout
{
    for(NSString *css in @[PaintSheet, [PaintSheet stringByAppendingString:IdSheet]])
    {
        std::mt19937 random(7);
        auto         body = [&](int frame) {
            std::string out = "<table data-lh-id=\"5\" class=\"fixed\">";
            int         id  = 10;
            for(int r = 0; r < 6; r++)
            {
                out += "<tr data-lh-id=\"" + std::to_string(id++) + "\">";
                for(int c = 0; c < 4; c++)
                {
                    const int  value = frame == 0 ? 100 : 95 + int(random() % 1000);
                    char       text[32];
                    snprintf(text, sizeof(text), "%d.%02d", value / 10, value % 100);
                    const bool up = random() % 2;
                    out += "<td data-lh-id=\"" + std::to_string(id) + "\" id=\"c" + std::to_string(id) + "\"" +
                           (frame == 0 ? std::string() : std::string(" class=\"") + (up ? "up" : "down") + "\"") + ">" + text + "</td>";
                    id++;
                }
                out += "</tr>";
            }
            return out + "</table><p data-lh-id=\"200\">Total <b data-lh-id=\"201\">" + std::to_string(frame * 37) + "</b> items</p>";
        };
        LHDocument *document = [[LHDocument alloc] initWithHTML:Page(css, body(0)) css:nil previous:nil];
        [document layoutWithMaxWidth:260 preferredWidth:260];
        int reused = 0;
        for(int frame = 1; frame < 30; frame++)
        {
            NSString   *html  = Page(css, body(frame));
            LHDocument *patch = [[LHDocument alloc] initWithHTML:html css:nil previous:document];
            LHDocument *fresh = [[LHDocument alloc] initWithHTML:html css:nil];
            reused += patch.reusedPrevious;
            [self expect:patch like:fresh width:260 label:[NSString stringWithFormat:@"frame %d", frame]];
            document = patch;
        }
        XCTAssertEqual(reused, 29);
    }
}

- (void)testPaintOnlyClassTogglesReuseTheLayout
{
    auto body = [](int frame, bool layoutClass) {
        std::string out = "<div data-lh-id=\"5\" class=\"row\">";
        for(int i = 0; i < 6; i++)
        {
            const bool  up  = ((frame >> i) & 1) != 0;
            std::string cls = up ? "up" : "down";
            if(layoutClass && i == 2)
                cls += " wide";
            out += "<span data-lh-id=\"" + std::to_string(10 + i) + "\" id=\"c" + std::to_string(i) + "\" class=\"" + cls + "\">" +
                   std::to_string(i * 11) + ".5</span>";
        }
        return out + "</div>";
    };
    LHDocument *document = [[LHDocument alloc] initWithHTML:Page(PaintSheet, body(0, false)) css:nil previous:nil];
    [document layoutWithMaxWidth:240 preferredWidth:240];
    for(int frame = 1; frame < 12; frame++)
    {
        const bool  layoutClass = frame % 4 == 3;
        NSString   *html        = Page(PaintSheet, body(frame * 7, layoutClass));
        LHDocument *patch       = [[LHDocument alloc] initWithHTML:html css:nil previous:document];
        LHDocument *fresh       = [[LHDocument alloc] initWithHTML:html css:nil];
        XCTAssertTrue(patch.reusedPrevious, @"frame %d", frame);
        if(!layoutClass && frame % 4 != 0)
            XCTAssertTrue(patch.reusedLayout, @"frame %d", frame);
        if(layoutClass)
            XCTAssertFalse(patch.reusedLayout, @"frame %d", frame);
        [self expect:patch like:fresh width:240 label:[NSString stringWithFormat:@"frame %d", frame]];
        document = patch;
    }
}

- (void)testSameSizeTextReusesTheLayout
{
    NSArray<NSString *> *values = @[@"10.25", @"10.75", @"38.00", @"38.00 x", @"5.5", @"5.5", @"41.09", @"1041.09", @"77.31"];
    auto body = [](NSString *value, bool up) {
        return std::string("<table data-lh-id=\"5\"><tr data-lh-id=\"6\"><td data-lh-id=\"7\" class=\"") + (up ? "up" : "down") +
               "\" style=\"font-family:Menlo\">" + value.UTF8String + "</td><td data-lh-id=\"8\">fixed</td></tr></table><p data-lh-id=\"9\">tail <b data-lh-id=\"10\" style=\"font-family:Menlo\">" +
               value.UTF8String + "</b></p>";
    };
    LHDocument *document = [[LHDocument alloc] initWithHTML:Page(PaintSheet, body(values[0], true)) css:nil previous:nil];
    [document layoutWithMaxWidth:200 preferredWidth:200];
    NSUInteger reused = 0;
    for(NSUInteger i = 1; i < values.count; i++)
    {
        NSString   *html  = Page(PaintSheet, body(values[i], i % 2));
        LHDocument *patch = [[LHDocument alloc] initWithHTML:html css:nil previous:document];
        LHDocument *fresh = [[LHDocument alloc] initWithHTML:html css:nil];
        XCTAssertTrue(patch.reusedPrevious, @"%@", values[i]);
        reused += patch.reusedLayout;
        if([values[i] isEqualToString:@"38.00 x"] || [values[i] isEqualToString:@"1041.09"] || [values[i] isEqualToString:@"5.5"])
            XCTAssertFalse(patch.reusedLayout && ![values[i - 1] isEqualToString:values[i]], @"%@", values[i]);
        [self expect:patch like:fresh width:200 label:values[i]];
        document = patch;
    }
    XCTAssertEqual(reused, 3u);
}

- (void)testRandomTextAndClassEditsMatchAFreshLayout
{
    const char  *budget   = getenv("TWIDGET_FUZZ_SECONDS");
    const double seconds  = budget ? atof(budget) : 0;
    NSDate      *deadline = [NSDate dateWithTimeIntervalSinceNow:seconds];
    int          frames = 0, reused = 0;
    for(unsigned seed = 1; seed <= 12 || [deadline timeIntervalSinceNow] > 0; seed++)
    {
        @autoreleasepool
        {
            std::mt19937 random(seed);
            NSString    *css = PaintSheet;
            if(seed % 3 == 1)
                css = [css stringByAppendingString:IdSheet];
            if(seed % 4 == 2)
                css = [css stringByAppendingString:ReachSheet];
            const char *classes[] = {"up", "down", "hot", "glow", "wide", "bold", "tag", "gone", "flt", "abs", "rel", "ib", "pre", "caps", "box"};
            const int   classCount = sizeof(classes) / sizeof(classes[0]);
            const char *words[]    = {"12.50", "7", "alpha beta", "longer line of words that wraps around", "x  y", "δ", "100.00", "  lead", "tail  "};
            const int   wordCount  = sizeof(words) / sizeof(words[0]);
            const char *styles[]   = {"", "color:#06c", "width:40%", "padding:2px", "background:#ffe", "font-size:14px"};
            int         next       = 10;
            auto        text_node  = [&]() {
                TextNode node;
                node.text = words[random() % wordCount];
                return node;
            };
            std::function<TextNode(int)> make = [&](int depth) {
                TextNode node;
                const int kind = depth > 2 ? 4 : random() % 6;
                node.id        = next++;
                if(random() % 3 == 0)
                    node.dom_id = "c" + std::to_string(node.id % 9);
                if(random() % 2)
                    node.classes.push_back(classes[random() % classCount]);
                switch(kind)
                {
                case 0:
                {
                    node.tag = "table";
                    if(random() % 2)
                        node.classes.push_back("fixed");
                    const int rows = 1 + random() % 3, cols = 1 + random() % 3;
                    for(int r = 0; r < rows; r++)
                    {
                        TextNode row;
                        row.tag = "tr";
                        row.id  = next++;
                        for(int c = 0; c < cols; c++)
                        {
                            TextNode cell;
                            cell.tag = "td";
                            cell.id  = next++;
                            if(random() % 2)
                                cell.classes.push_back(classes[random() % classCount]);
                            cell.children.push_back(text_node());
                            row.children.push_back(std::move(cell));
                        }
                        node.children.push_back(std::move(row));
                    }
                    return node;
                }
                case 1:
                    node.tag = "div";
                    node.classes.push_back("row");
                    break;
                case 2:
                    node.tag = "p";
                    break;
                case 3:
                    node.tag = "section";
                    node.classes.push_back("box");
                    break;
                default:
                    node.tag = random() % 2 ? "span" : "b";
                    break;
                }
                const int count = 1 + random() % 3;
                for(int i = 0; i < count; i++)
                {
                    if(depth < 3 && random() % 3 == 0)
                        node.children.push_back(make(depth + 1));
                    else
                        node.children.push_back(text_node());
                }
                return node;
            };
            TextNode root;
            root.tag = "div";
            root.id  = 5;
            root.style = "position:relative";
            for(int i = 0; i < 4; i++)
                root.children.push_back(make(0));
            auto html = [&]() {
                std::string out;
                Write(root, out);
                return Page(css, out);
            };
            const CGFloat width    = 180 + random() % 140;
            LHDocument   *document = [[LHDocument alloc] initWithHTML:html() css:nil previous:nil];
            [document layoutWithMaxWidth:width preferredWidth:width];
            for(int step = 0; step < 30; step++)
            {
                const int edits = 1 + random() % 4;
                for(int e = 0; e < edits; e++)
                {
                    std::vector<TextNode *> texts, elements;
                    Gather(root, texts, elements);
                    switch(random() % 6)
                    {
                    case 0:
                    case 1:
                        if(!texts.empty())
                        {
                            TextNode   *text  = texts[random() % texts.size()];
                            std::string value = text->text;
                            for(char &c : value)
                            {
                                if(c >= '0' && c <= '9')
                                    c = char('0' + random() % 10);
                            }
                            text->text = value == text->text ? words[random() % wordCount] : value;
                        }
                        break;
                    case 2:
                        if(!texts.empty())
                            texts[random() % texts.size()]->text = words[random() % wordCount];
                        break;
                    case 3:
                    {
                        TextNode *element = elements[random() % elements.size()];
                        if(element->tag == "tr" || element->tag == "table")
                            break;
                        const std::string name = random() % 3 ? (random() % 2 ? "up" : "down") : classes[random() % classCount];
                        auto              it   = std::find(element->classes.begin(), element->classes.end(), name);
                        if(it == element->classes.end())
                        {
                            element->classes.erase(std::remove(element->classes.begin(), element->classes.end(), name == "up" ? "down" : "up"),
                                                   element->classes.end());
                            element->classes.push_back(name);
                        } else
                            element->classes.erase(it);
                        break;
                    }
                    case 4:
                        elements[random() % elements.size()]->style = styles[random() % 6];
                        break;
                    default:
                    {
                        TextNode *element = elements[random() % elements.size()];
                        if(element->tag != "tr" && element->tag != "table")
                            element->children.push_back(text_node());
                        break;
                    }
                    }
                }
                NSString   *page  = html();
                LHDocument *patch = [[LHDocument alloc] initWithHTML:page css:nil previous:document];
                LHDocument *fresh = [[LHDocument alloc] initWithHTML:page css:nil];
                frames++;
                reused += patch.reusedPrevious;
                [self expect:patch like:fresh width:width label:[NSString stringWithFormat:@"seed %u step %d %@", seed, step, page]];
                document = patch;
            }
        }
    }
    NSLog(@"TEXTLAYOUT FUZZ frames=%d patched=%d", frames, reused);
    XCTAssertGreaterThan(reused, frames / 2);
}

@end
