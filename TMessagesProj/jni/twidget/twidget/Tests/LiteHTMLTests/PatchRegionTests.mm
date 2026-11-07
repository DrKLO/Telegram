#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <functional>
#include <random>
#include <string>
#include <vector>

@interface PatchRegionTests : XCTestCase
@end

@implementation PatchRegionTests

static NSString *const RegionAttribute = @"data-lh-id";
static NSString *const RegionSheet = @"body{margin:4px;font:12px -apple-system}.up{color:#34c759}.down{color:#ff3b30}.hot{background:#fee}"
                                      ".it{font-style:italic;font-family:Georgia}.tight{line-height:7px}.big{font-size:20px}.mono{font-family:Menlo}"
                                      ".glow{text-shadow:0 0 6px #00f}.fade{opacity:.6}.turn{transform:rotate(3deg)}.over{white-space:nowrap;width:30px}"
                                      ".rel{position:relative;left:3px;top:-2px}.ib{display:inline-block;min-width:20px}.abs{position:absolute;right:2px;top:2px}"
                                      ".hidden{display:none}.caps{text-transform:uppercase}.under{text-decoration:underline}.clip{overflow:hidden;height:9px}"
                                      "table{border-collapse:collapse}td{padding:1px 3px;text-align:right}.row{display:flex;gap:4px}.edge{border:1px solid}.ec{border-color:#f0f}"
                                      ".tint{color:#909}.bg{background:linear-gradient(#fff,#eef)}.shadow{box-shadow:0 0 4px}.line{outline:1px solid}li{margin-left:20px}";

struct RegionNode
{
    std::string             tag;
    int                     id = 0;
    std::string             cls;
    std::string             style;
    std::string             text;
    std::vector<RegionNode> children;
};

static void RegionEscape(const std::string &text, std::string &out)
{
    for(char c : text)
    {
        if(c == '<')
            out += "&lt;";
        else if(c == '&')
            out += "&amp;";
        else if(c == '>')
            out += "&gt;";
        else
            out += c;
    }
}

static void RegionWrite(const RegionNode &node, std::string &out)
{
    if(node.tag.empty())
    {
        RegionEscape(node.text, out);
        return;
    }
    out += "<" + node.tag + " data-lh-id=\"" + std::to_string(node.id) + "\"";
    if(!node.cls.empty())
        out += " class=\"" + node.cls + "\"";
    if(!node.style.empty())
        out += " style=\"" + node.style + "\"";
    out += ">";
    for(const auto &child : node.children)
        RegionWrite(child, out);
    out += "</" + node.tag + ">";
}

static NSString *RegionPage(const RegionNode &root)
{
    std::string body;
    RegionWrite(root, body);
    return [NSString stringWithFormat:@"<!DOCTYPE html><html data-lh-id=\"1\"><head data-lh-id=\"2\"><style data-lh-id=\"3\">%@</style></head>"
                                       "<body data-lh-id=\"4\">%@</body></html>",
                                       RegionSheet, @(body.c_str())];
}

static void RegionGather(RegionNode &node, std::vector<RegionNode *> &elements)
{
    if(!node.tag.empty())
        elements.push_back(&node);
    for(auto &child : node.children)
        RegionGather(child, elements);
}

static NSData *RegionPixels(LHDocument *document, CGFloat width, size_t *stride = nullptr)
{
    CGSize     size  = [document layoutWithMaxWidth:width preferredWidth:width];
    CGImageRef image = [document copyImageWithMaxWidth:width rect:CGRectMake(0, 0, width, MAX(1, ceil(size.height))) scale:2];
    if(!image)
        return [NSData data];
    NSData *data = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    if(stride)
        *stride = CGImageGetBytesPerRow(image);
    CGImageRelease(image);
    return data;
}

static const char *const RegionClasses[] = {"up", "down", "hot", "it", "tight", "big", "mono", "glow", "fade", "turn", "over", "rel", "ib", "abs", "hidden", "caps", "under", "clip", "edge", "ec", "tint", "bg", "shadow", "line", "up edge", "down ib", "tint shadow", "hot line"};
static const char *const RegionWords[]   = {"12.50", "7", "alpha beta", "Wyj fg", "x  y", "δ", "100.00", "ÅÉ ÿ", "lead"};

static RegionNode RegionDocument(std::mt19937 &random, bool soleTexts)
{
    int  next = 10;
    auto leaf = [&](const std::string &tag) {
        RegionNode node;
        node.tag = tag;
        node.id  = next++;
        if(random() % 2)
            node.cls = RegionClasses[random() % (sizeof(RegionClasses) / sizeof(RegionClasses[0]))];
        RegionNode text;
        text.text = RegionWords[random() % (sizeof(RegionWords) / sizeof(RegionWords[0]))];
        node.children.push_back(text);
        return node;
    };
    std::function<RegionNode(int)> make = [&](int depth) {
        RegionNode node;
        node.id = next++;
        if(random() % 2)
            node.cls = RegionClasses[random() % (sizeof(RegionClasses) / sizeof(RegionClasses[0]))];
        if(random() % 4 == 0)
            node.style = random() % 2 ? "background-color:#eef" : "color:#06c";
        switch(depth > 2 ? 3 : random() % 4)
        {
        case 0:
        {
            node.tag = "table";
            for(int r = 0; r < 2; r++)
            {
                RegionNode row;
                row.tag = "tr";
                row.id  = next++;
                for(int c = 0; c < 3; c++)
                    row.children.push_back(leaf("td"));
                node.children.push_back(row);
            }
            return node;
        }
        case 1:
            node.tag = random() % 2 ? "div" : "p";
            break;
        case 2:
            node.tag = "div";
            node.cls = "row";
            break;
        default:
            return leaf(random() % 3 == 0 ? "b" : (random() % 4 == 0 ? "li" : (random() % 2 ? "span" : "div")));
        }
        const int count = 1 + random() % 3;
        for(int i = 0; i < count; i++)
        {
            if(soleTexts || random() % 2)
                node.children.push_back(make(depth + 1));
            else
                node.children.push_back(leaf("span").children[0]);
        }
        return node;
    };
    RegionNode root;
    root.tag = "div";
    root.id  = 5;
    for(int i = 0; i < 4; i++)
        root.children.push_back(make(0));
    return root;
}

static std::string RegionRetext(std::mt19937 &random, const std::string &text)
{
    std::string value = text;
    for(char &c : value)
    {
        if(c >= '0' && c <= '9')
            c = char('0' + random() % 10);
    }
    if(value == text || random() % 4 == 0)
        value = RegionWords[random() % (sizeof(RegionWords) / sizeof(RegionWords[0]))];
    return value;
}

- (BOOL)covered:(NSData *)before after:(NSData *)after stride:(size_t)stride rects:(NSArray<NSValue *> *)rects width:(CGFloat)width label:(NSString *)label
{
    const size_t pw = (size_t)(width * 2), row = stride / 4, rows = before.length / stride;
    const uint32_t *a = (const uint32_t *)before.bytes, *b = (const uint32_t *)after.bytes;
    for(size_t y = 0; y < rows; y++)
    {
        for(size_t x = 0; x < pw; x++)
        {
            if(a[y * row + x] == b[y * row + x])
                continue;
            const CGPoint point  = CGPointMake((x + 0.5) / 2, (y + 0.5) / 2);
            BOOL          inside = NO;
            for(NSValue *value in rects)
                inside = inside || CGRectContainsPoint(value.rectValue, point);
            if(!inside)
            {
                XCTFail(@"pixel %zu,%zu changed outside %@ in %@", x, y, rects, label);
                return NO;
            }
        }
    }
    return YES;
}

- (void)testRandomPatchRegionsCoverEveryChangedPixel
{
    const char  *budget   = getenv("TWIDGET_FUZZ_SECONDS");
    NSDate      *deadline = [NSDate dateWithTimeIntervalSinceNow:budget ? atof(budget) / 2 : 0];
    int          confined = 0, textFrames = 0, classFrames = 0;
    for(unsigned seed = 1; seed <= 60 || [deadline timeIntervalSinceNow] > 0; seed++)
    {
        @autoreleasepool
        {
            std::mt19937  random(seed);
            RegionNode    root     = RegionDocument(random, seed % 2);
            const CGFloat width    = 160 + random() % 160;
            LHDocument   *document = [[LHDocument alloc] initWithHTML:RegionPage(root) css:nil previous:nil];
            [document layoutWithMaxWidth:width preferredWidth:width];
            NSString     *previousPage = RegionPage(root);
            for(int step = 0; step < 16; step++)
            {
                std::vector<RegionNode *> elements;
                RegionGather(root, elements);
                bool       textEdit  = false;
                bool       classEdit = false;
                const bool textOnly  = random() % 2;
                for(int e = 0, edits = 1 + random() % 3; e < edits; e++)
                {
                    RegionNode *element = elements[random() % elements.size()];
                    switch(textOnly ? 0 : random() % 4)
                    {
                    case 0:
                    case 1:
                        for(auto &child : element->children)
                        {
                            if(child.tag.empty())
                            {
                                child.text = RegionRetext(random, child.text);
                                textEdit   = true;
                                break;
                            }
                        }
                        break;
                    case 2:
                        if(element->tag != "tr" && element->tag != "table")
                        {
                            element->cls = random() % 3 ? (element->cls == "up" ? "down" : (element->cls == "down" ? "tint" : "up")) : RegionClasses[random() % (sizeof(RegionClasses) / sizeof(RegionClasses[0]))];
                            classEdit    = true;
                        }
                        break;
                    default:
                        element->style = random() % 3 == 0 ? "" : (random() % 2 ? "background-color:#ffe" : (random() % 2 ? "color:#0a0" : "color:#a0a"));
                        break;
                    }
                }
                size_t      stride = 0;
                NSData     *before = RegionPixels(document, width, &stride);
                NSString   *page   = RegionPage(root);
                LHDocument *patch  = [[LHDocument alloc] initWithHTML:page css:nil previous:document];
                NSArray<NSValue *> *rects = patch.patchDirtyRects;
                NSData     *after  = RegionPixels(patch, width);
                NSString   *label  = [NSString stringWithFormat:@"seed %u step %d width %.0f %@ after %@", seed, step, width, page, previousPage];
                previousPage       = page;
                XCTAssertEqualObjects(after, RegionPixels([[LHDocument alloc] initWithHTML:page css:nil], width), @"%@", label);
                if(rects && before.length == after.length)
                {
                    confined++;
                    textFrames += textEdit;
                    classFrames += classEdit;
                    if(![self covered:before after:after stride:stride rects:rects width:width label:label])
                        return;
                }
                document = patch;
            }
        }
    }
    NSLog(@"PATCH REGIONS confined=%d with text=%d with class=%d", confined, textFrames, classFrames);
    XCTAssertGreaterThan(textFrames, 20);
    XCTAssertGreaterThan(classFrames, 2);
}

- (void)testRandomLayoutRegionsCoverEveryChangedPixel
{
    const char  *budget   = getenv("TWIDGET_FUZZ_SECONDS");
    NSDate      *deadline = [NSDate dateWithTimeIntervalSinceNow:budget ? atof(budget) / 2 : 0];
    int          confined = 0, frames = 0;
    for(unsigned seed = 1; seed <= 60 || [deadline timeIntervalSinceNow] > 0; seed++)
    {
        @autoreleasepool
        {
            std::mt19937  random(seed + 1000);
            RegionNode    root     = RegionDocument(random, seed % 2);
            const CGFloat width    = 160 + random() % 160;
            LHDocument   *document = [[LHDocument alloc] initWithHTML:RegionPage(root) css:nil previous:nil];
            [document layoutWithMaxWidth:width preferredWidth:width];
            for(int step = 0; step < 16; step++)
            {
                std::vector<RegionNode *> elements;
                RegionGather(root, elements);
                for(int e = 0, edits = 1 + random() % 2; e < edits; e++)
                {
                    RegionNode *element = elements[random() % elements.size()];
                    if(random() % 3)
                    {
                        for(auto &child : element->children)
                        {
                            if(child.tag.empty())
                            {
                                child.text = RegionRetext(random, child.text);
                                break;
                            }
                        }
                    } else if(element->tag != "tr" && element->tag != "table")
                        element->cls = RegionClasses[random() % (sizeof(RegionClasses) / sizeof(RegionClasses[0]))];
                }
                size_t      stride = 0;
                NSData     *before = RegionPixels(document, width, &stride);
                NSString   *page   = RegionPage(root);
                LHDocument *patch  = [[LHDocument alloc] initWithHTML:page css:nil previous:document];
                NSData     *after  = RegionPixels(patch, width);
                NSArray<NSValue *> *rects = patch.reusedLayout ? nil : patch.layoutDirtyRects;
                NSString   *label  = [NSString stringWithFormat:@"seed %u step %d width %.0f %@", seed, step, width, page];
                frames++;
                if(rects && before.length == after.length)
                {
                    confined++;
                    if(![self covered:before after:after stride:stride rects:rects width:width label:label])
                        return;
                }
                document = patch;
            }
        }
    }
    NSLog(@"LAYOUT REGIONS confined=%d of %d", confined, frames);
    XCTAssertGreaterThan(confined, 10);
}

- (void)testColorClassTogglesRepaintOnlyTheirCells
{
    auto page = [](const std::vector<int> &state) {
        std::string body = "<table data-lh-id=\"5\">";
        for(int r = 0; r < 6; r++)
        {
            body += "<tr data-lh-id=\"" + std::to_string(100 + r) + "\">";
            for(int c = 0; c < 4; c++)
            {
                const int i = r * 4 + c;
                const char *cls[] = {"up", "down", "tint", "up edge", "down edge", "bg", "hot"};
                body += "<td data-lh-id=\"" + std::to_string(10 + i) + "\" class=\"" + cls[state[i]] + "\">" + std::to_string(i * 7) + ".25</td>";
            }
            body += "</tr>";
        }
        body += "</table><ul data-lh-id=\"200\"><li data-lh-id=\"201\" class=\"" + std::string(state[24] % 2 ? "up" : "down") + "\">item</li></ul>";
        RegionNode root;
        root.tag = "div";
        root.id  = 6;
        RegionNode text;
        text.text = "x";
        root.children.push_back(text);
        std::string html = [RegionPage(root) UTF8String];
        const std::string marker = "<div data-lh-id=\"6\">x</div>";
        html.replace(html.find(marker), marker.size(), body);
        return @(html.c_str());
    };
    std::mt19937     random(3);
    std::vector<int> state(25, 0);
    LHDocument      *document = [[LHDocument alloc] initWithHTML:page(state) css:nil previous:nil];
    [document layoutWithMaxWidth:260 preferredWidth:260];
    int confined = 0;
    for(int step = 0; step < 40; step++)
    {
        for(int e = 0, edits = 1 + random() % 3; e < edits; e++)
        {
            const int i = random() % 25;
            state[i]    = i == 24 ? state[i] + 1 : (state[i] < 3 ? (state[i] + 1) % 3 : (state[i] == 3 ? 4 : (state[i] == 4 ? 3 : (state[i] == 5 ? 6 : 5))));
        }
        size_t      stride = 0;
        NSData     *before = RegionPixels(document, 260, &stride);
        NSString   *html   = page(state);
        LHDocument *patch  = [[LHDocument alloc] initWithHTML:html css:nil previous:document];
        NSArray<NSValue *> *rects = patch.patchDirtyRects;
        NSData     *after  = RegionPixels(patch, 260);
        XCTAssertEqualObjects(after, RegionPixels([[LHDocument alloc] initWithHTML:html css:nil], 260), @"step %d", step);
        if(rects && before.length == after.length)
        {
            confined++;
            if(![self covered:before after:after stride:stride rects:rects width:260 label:html])
                return;
        }
        document = patch;
    }
    XCTAssertGreaterThan(confined, 10);
}

- (void)testTextRegionsStayWithinTheTextBoxes
{
    auto page = [](const char *value) {
        return [NSString stringWithFormat:@"<!DOCTYPE html><style>body{margin:0;font:12px Menlo}td{padding:2px}</style><table data-lh-id=\"1\"><tr><td data-lh-id=\"2\">%s</td>"
                                           "<td data-lh-id=\"3\">fixed</td></tr></table><div data-lh-id=\"4\" style=\"height:300px;background:#eee\">tall</div>", value];
    };
    LHDocument *previous = [[LHDocument alloc] initWithHTML:page("10.25") css:nil previous:nil];
    [previous layoutWithMaxWidth:300 preferredWidth:300];
    size_t      stride = 0;
    NSData     *before = RegionPixels(previous, 300, &stride);
    LHDocument *next   = [[LHDocument alloc] initWithHTML:page("10.75") css:nil previous:previous];
    XCTAssertTrue(next.reusedLayout);
    NSArray<NSValue *> *rects = next.patchDirtyRects;
    XCTAssertEqual(rects.count, 1u);
    CGRect cell = [[next boundsByAttribute:RegionAttribute][@"2"] rectValue];
    XCTAssertTrue(CGRectIntersectsRect(rects.firstObject.rectValue, cell));
    XCTAssertLessThan(CGRectGetMaxY(rects.firstObject.rectValue), 40);
    [self covered:before after:RegionPixels(next, 300) stride:stride rects:rects width:300 label:@"cell"];
    for(NSString *style in @[@"text-shadow:0 0 4px red", @"transform:translateX(2px)", @"opacity:.5"])
    {
        NSString *(^styled)(const char *) = ^NSString *(const char *value) {
            return [NSString stringWithFormat:@"<!DOCTYPE html><div style=\"%@\"><span data-lh-id=\"1\" style=\"font-family:Menlo\">%s</span></div>", style, value];
        };
        LHDocument *a = [[LHDocument alloc] initWithHTML:styled("1.5") css:nil previous:nil];
        [a layoutWithMaxWidth:200 preferredWidth:200];
        LHDocument *b = [[LHDocument alloc] initWithHTML:styled("2.5") css:nil previous:a];
        XCTAssertTrue(b.reusedLayout, @"%@", style);
        XCTAssertNil(b.patchDirtyRects, @"%@", style);
    }
}

static void RegionChange(NSMutableData *changes, int idValue, NSString *name, NSString *value)
{
    for(NSString *part in @[[NSString stringWithFormat:@"%d", idValue], name, value])
    {
        [changes appendData:[part dataUsingEncoding:NSUTF8StringEncoding]];
        [changes appendBytes:"" length:1];
    }
}

- (void)testRandomHolePatchesMatchHTMLPatches
{
    const char *budget   = getenv("TWIDGET_FUZZ_SECONDS");
    NSDate     *deadline = [NSDate dateWithTimeIntervalSinceNow:budget ? atof(budget) / 2 : 0];
    int         frames = 0, patched = 0;
    for(unsigned seed = 1; seed <= 40 || [deadline timeIntervalSinceNow] > 0; seed++)
    {
        @autoreleasepool
        {
            std::mt19937  random(seed + 1000);
            RegionNode    root     = RegionDocument(random, true);
            const CGFloat width    = 160 + random() % 160;
            LHDocument   *document = [[LHDocument alloc] initWithHTML:RegionPage(root) css:nil previous:nil];
            LHDocument   *mirror   = [[LHDocument alloc] initWithHTML:RegionPage(root) css:nil previous:nil];
            [document layoutWithMaxWidth:width preferredWidth:width];
            [mirror layoutWithMaxWidth:width preferredWidth:width];
            for(int step = 0; step < 16; step++)
            {
                std::vector<RegionNode *> elements;
                RegionGather(root, elements);
                NSMutableData *changes = [NSMutableData data];
                std::vector<int> touched;
                for(int e = 0, edits = 1 + random() % 4; e < edits; e++)
                {
                    RegionNode *element = elements[random() % elements.size()];
                    if(std::find(touched.begin(), touched.end(), element->id) != touched.end())
                        continue;
                    const int kind = random() % 3;
                    if(kind == 0 && element->children.size() == 1 && element->children[0].tag.empty())
                    {
                        element->children[0].text = RegionRetext(random, element->children[0].text);
                        std::string escaped;
                        RegionEscape(element->children[0].text, escaped);
                        RegionChange(changes, element->id, @"", @(escaped.c_str()));
                    } else if(kind == 1 && !element->cls.empty() && element->tag != "tr" && element->tag != "table")
                    {
                        element->cls = random() % 3 ? (element->cls == "up" ? "down" : "up") : RegionClasses[random() % (sizeof(RegionClasses) / sizeof(RegionClasses[0]))];
                        RegionChange(changes, element->id, @"class", @(element->cls.c_str()));
                    } else if(kind == 2 && !element->style.empty())
                    {
                        element->style = random() % 2 ? "background-color:#ffe" : (random() % 2 ? "padding:2px" : "color:#a0a");
                        RegionChange(changes, element->id, @"style", @(element->style.c_str()));
                    } else
                        continue;
                    touched.push_back(element->id);
                }
                if(changes.length == 0)
                    continue;
                frames++;
                NSString   *page  = RegionPage(root);
                LHDocument *patch = [[LHDocument alloc] initWithPrevious:document changes:changes attribute:RegionAttribute css:nil];
                if(patch)
                {
                    patched++;
                    XCTAssertEqualObjects(patch.html, page, @"seed %u step %d", seed, step);
                } else
                    patch = [[LHDocument alloc] initWithHTML:page css:nil previous:document];
                LHDocument *fresh = [[LHDocument alloc] initWithHTML:page css:nil previous:mirror];
                NSString   *label = [NSString stringWithFormat:@"seed %u step %d %@", seed, step, page];
                XCTAssertEqualObjects(RegionPixels(patch, width), RegionPixels(fresh, width), @"%@", label);
                XCTAssertEqualObjects(patch.layoutSignature, fresh.layoutSignature, @"%@", label);
                document = patch;
                mirror   = fresh;
            }
        }
    }
    NSLog(@"HOLE PATCHES frames=%d patched=%d", frames, patched);
    XCTAssertGreaterThan(patched, frames * 3 / 4);
}

- (void)testLeadingNewlineInPreformattedTextMatchesAFreshParse
{
    for(NSString *tag in @[@"pre", @"listing", @"textarea", @"div"])
    {
        NSString   *(^page)(NSString *) = ^NSString *(NSString *text) {
            return [NSString stringWithFormat:@"<!DOCTYPE html><%@>%@</%@><p>after</p>", tag, text, tag];
        };
        LHDocument *previous = [[LHDocument alloc] initWithHTML:page(@"five") css:nil previous:nil];
        [previous layoutWithMaxWidth:200 preferredWidth:200];
        LHDocument *patch = [[LHDocument alloc] initWithHTML:page(@"\nfive") css:nil previous:previous];
        LHDocument *fresh = [[LHDocument alloc] initWithHTML:page(@"\nfive") css:nil];
        XCTAssertEqualObjects(RegionPixels(patch, 200), RegionPixels(fresh, 200), @"%@", tag);
        XCTAssertEqualObjects(patch.layoutSignature, fresh.layoutSignature, @"%@", tag);
    }
}

- (void)testHolePatchesRefuseWhatTheHTMLCouldNotCarry
{
    NSString *page = @"<!DOCTYPE html><div data-lh-id=\"1\" class=\"a\" style=\"color:red\">one</div><p data-lh-id=\"2\">two <b data-lh-id=\"3\">three</b></p>"
                      "<span data-lh-id=\"4\">four</span><pre data-lh-id=\"5\">five</pre><div data-lh-id=\"6\">   </div>";
    NSArray<NSArray<NSString *> *> *refused = @[
        @[@"9", @"", @"nine"], @[@"5", @"", @"\nfive"], @[@"4", @"class", @"x"], @[@"1", @"title", @"x"], @[@"1", @"", @"a<b"],
        @[@"1", @"class", @"a\"b"], @[@"1", @"", @"a &amp; b"], @[@"1", @"", @"  "], @[@"6", @"", @"six"], @[@"1", @"", @""],
    ];
    for(NSArray<NSString *> *change in refused)
    {
        LHDocument *previous = [[LHDocument alloc] initWithHTML:page css:nil previous:nil];
        [previous layoutWithMaxWidth:200 preferredWidth:200];
        NSMutableData *changes = [NSMutableData data];
        RegionChange(changes, change[0].intValue, change[1], change[2]);
        XCTAssertNil([[LHDocument alloc] initWithPrevious:previous changes:changes attribute:RegionAttribute css:nil], @"%@", change);
        XCTAssertEqualObjects(RegionPixels(previous, 200), RegionPixels([[LHDocument alloc] initWithHTML:page css:nil], 200), @"%@", change);
        LHDocument *next = [[LHDocument alloc] initWithHTML:page css:nil previous:previous];
        XCTAssertTrue(next.reusedPrevious, @"%@", change);
    }
    LHDocument *previous = [[LHDocument alloc] initWithHTML:page css:nil previous:nil];
    [previous layoutWithMaxWidth:200 preferredWidth:200];
    NSMutableData *changes = [NSMutableData data];
    RegionChange(changes, 1, @"", @"uno");
    RegionChange(changes, 1, @"class", @"b");
    RegionChange(changes, 3, @"", @"tres");
    RegionChange(changes, 1, @"style", @"color:blue");
    LHDocument *next = [[LHDocument alloc] initWithPrevious:previous changes:changes attribute:RegionAttribute css:nil];
    NSString   *want = @"<!DOCTYPE html><div data-lh-id=\"1\" class=\"b\" style=\"color:blue\">uno</div><p data-lh-id=\"2\">two <b data-lh-id=\"3\">tres</b></p>"
                        "<span data-lh-id=\"4\">four</span><pre data-lh-id=\"5\">five</pre><div data-lh-id=\"6\">   </div>";
    XCTAssertEqualObjects(next.html, want);
    XCTAssertEqualObjects(RegionPixels(next, 200), RegionPixels([[LHDocument alloc] initWithHTML:want css:nil], 200));
    RegionChange(changes, 1, @"", @"again");
    XCTAssertNil([[LHDocument alloc] initWithPrevious:next changes:changes attribute:RegionAttribute css:nil]);
}

@end
