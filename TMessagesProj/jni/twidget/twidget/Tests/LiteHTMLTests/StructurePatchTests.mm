#import <XCTest/XCTest.h>
#import "LHDocument.h"

#include <algorithm>
#include <functional>
#include <memory>
#include <random>
#include <string>
#include <vector>

@interface StructurePatchTests : XCTestCase
@end

@implementation StructurePatchTests

static NSString *const Sheet = @".on{color:#c00;padding:2px}.on .x{background:#cfc}.big{font-size:20px}.card{border:1px solid #888;margin:2px}"
                                "span.x{font-weight:bold}.x::before{content:\"*\"}.dark{--c:#00f}.y{color:var(--c,#0a0)}"
                                "section.on>p{margin-left:12px}.badge{display:inline-block;width:14px;height:10px;background:#fa0}"
                                "[data-k] span{color:#909}.card [data-k=\"2\"]{outline:1px solid #00f}ul>li.x{list-style:square}"
                                ":not(.on)>b{text-decoration:underline}.on b::after{content:\"!\"}td.on{background:#ffd}button.x{border:2px solid #0c0}";
static NSString *const SiblingSheet = @".x+.y{margin-left:6px}li:first-child{color:#090}.on~.x{text-decoration:underline}"
                                       "div:nth-child(2n){background:#eef}p:last-child{border-bottom:2px solid #000}";

static NSData *Pixels(LHDocument *document)
{
    CGSize     size  = [document layoutWithMaxWidth:240 preferredWidth:240];
    CGImageRef image = [document copyImageWithMaxWidth:240 rect:CGRectMake(0, 0, 240, MAX(1, ceil(size.height))) scale:2];
    if(!image)
        return [NSData data];
    NSData *data = (__bridge_transfer NSData *)CGDataProviderCopyData(CGImageGetDataProvider(image));
    CGImageRelease(image);
    return data;
}

static LHDocument *Laid(NSString *html, LHDocument *previous)
{
    LHDocument *document = [[LHDocument alloc] initWithHTML:html css:nil previous:previous];
    [document layoutWithMaxWidth:240 preferredWidth:240];
    return document;
}

static NSString *Wrap(NSString *css, NSString *body)
{
    return [NSString stringWithFormat:@"<!DOCTYPE html><html data-lh-id=\"1\"><head data-lh-id=\"2\"><style data-lh-id=\"3\">%@</style></head>"
                                       "<body data-lh-id=\"4\" style=\"margin:4px;font:13px -apple-system\">%@</body></html>",
                                       css, body];
}

- (void)expectTree:(LHDocument *)patched like:(LHDocument *)fresh label:(NSString *)label
{
    for(NSString *attribute in @[@"data-lh-id", @"class", @"hidden", @"style", @"src", @"href", @"onclick", @"onload", @"srcdoc", @"id"])
        XCTAssertEqualObjects([patched parentsByAttribute:attribute], [fresh parentsByAttribute:attribute], @"%@ %@", label, attribute);
}

- (void)checkSequence:(NSArray<NSString *> *)bodies css:(NSString *)css expectReuse:(BOOL)expectReuse
{
    LHDocument *document = Laid(Wrap(css, bodies.firstObject), nil);
    for(NSUInteger i = 1; i < bodies.count; i++)
    {
        NSString   *html  = Wrap(css, bodies[i]);
        LHDocument *next  = Laid(html, document);
        LHDocument *fresh = Laid(html, nil);
        if(expectReuse)
            XCTAssertTrue(next.reusedPrevious, @"step %lu %@", (unsigned long)i, bodies[i]);
        XCTAssertEqualObjects(Pixels(next), Pixels(fresh), @"step %lu %@", (unsigned long)i, bodies[i]);
        [self expectTree:next like:fresh label:bodies[i]];
        document = next;
    }
}

- (void)testClassTogglesMatchAFreshParse
{
    NSArray<NSString *> *bodies = @[
        @"<section data-lh-id=\"5\" class=\"card\"><p data-lh-id=\"6\">one <span data-lh-id=\"7\" class=\"x\">two</span></p><p data-lh-id=\"8\" class=\"y\">three</p></section>",
        @"<section data-lh-id=\"5\" class=\"card on\"><p data-lh-id=\"6\">one <span data-lh-id=\"7\" class=\"x\">two</span></p><p data-lh-id=\"8\" class=\"y\">three</p></section>",
        @"<section data-lh-id=\"5\" class=\"card on dark\"><p data-lh-id=\"6\">one <span data-lh-id=\"7\" class=\"x big\">two</span></p><p data-lh-id=\"8\" class=\"y\">three</p></section>",
        @"<section data-lh-id=\"5\" class=\"card\"><p data-lh-id=\"6\" class=\"x\">one <span data-lh-id=\"7\">two</span></p><p data-lh-id=\"8\" class=\"y big\">three</p></section>",
        @"<section data-lh-id=\"5\" class=\"dark\"><p data-lh-id=\"6\" class=\"x\" style=\"padding:3px\">one <span data-lh-id=\"7\">two</span></p><p data-lh-id=\"8\" class=\"y\">three</p></section>",
    ];
    [self checkSequence:bodies css:Sheet expectReuse:YES];
    [self checkSequence:bodies css:[Sheet stringByAppendingString:SiblingSheet] expectReuse:YES];
}

- (void)testHiddenTogglesMatchAFreshParse
{
    NSArray<NSString *> *bodies = @[
        @"<div data-lh-id=\"5\"><span data-lh-id=\"6\" class=\"badge\"></span><div data-lh-id=\"7\" class=\"x\">a</div><div data-lh-id=\"8\" class=\"y\">b</div></div>",
        @"<div data-lh-id=\"5\"><span data-lh-id=\"6\" class=\"badge\" hidden=\"\"></span><div data-lh-id=\"7\" class=\"x\">a</div><div data-lh-id=\"8\" class=\"y\">b</div></div>",
        @"<div data-lh-id=\"5\"><span data-lh-id=\"6\" class=\"badge\" hidden=\"\"></span><div data-lh-id=\"7\" class=\"x\" hidden=\"\">a</div><div data-lh-id=\"8\" class=\"y\">b</div></div>",
        @"<div data-lh-id=\"5\" hidden=\"\"><span data-lh-id=\"6\" class=\"badge\"></span><div data-lh-id=\"7\" class=\"x\">a</div><div data-lh-id=\"8\" class=\"y\">b</div></div>",
        @"<div data-lh-id=\"5\"><span data-lh-id=\"6\" class=\"badge\"></span><div data-lh-id=\"7\" class=\"x\">a</div><div data-lh-id=\"8\" class=\"y\">b</div></div>",
    ];
    [self checkSequence:bodies css:Sheet expectReuse:YES];
    [self checkSequence:bodies css:[Sheet stringByAppendingString:SiblingSheet] expectReuse:YES];
}

- (void)testInsertsRemovesAndMovesMatchAFreshParse
{
    NSString *(^row)(int, NSString *) = ^NSString *(int key, NSString *cls) {
        return [NSString stringWithFormat:@"<li data-lh-id=\"%d\" class=\"%@\">row %d <b data-lh-id=\"%d\">!</b></li>", key, cls, key, key + 1000];
    };
    NSArray<NSString *> *bodies = @[
        [NSString stringWithFormat:@"<ul data-lh-id=\"5\">%@%@%@</ul><p data-lh-id=\"6\">tail</p>", row(10, @"x"), row(11, @"y"), row(12, @"x")],
        [NSString stringWithFormat:@"<ul data-lh-id=\"5\">%@%@%@%@</ul><p data-lh-id=\"6\">tail</p>", row(9, @"y"), row(10, @"x"), row(11, @"y"), row(12, @"x")],
        [NSString stringWithFormat:@"<ul data-lh-id=\"5\">%@%@%@</ul><p data-lh-id=\"6\">tail</p>", row(9, @"y"), row(11, @"y on"), row(12, @"x")],
        [NSString stringWithFormat:@"<ul data-lh-id=\"5\">%@%@%@</ul><p data-lh-id=\"6\">tail</p>", row(12, @"x"), row(9, @"y"), row(11, @"y on")],
        [NSString stringWithFormat:@"<ul data-lh-id=\"5\">%@</ul><p data-lh-id=\"6\">tail</p><p data-lh-id=\"7\">more</p>", row(12, @"x")],
        [NSString stringWithFormat:@"<ul data-lh-id=\"5\"></ul><p data-lh-id=\"6\">tail</p>"],
        [NSString stringWithFormat:@"<ul data-lh-id=\"5\">%@%@</ul><p data-lh-id=\"6\">tail</p>", row(20, @"x"), row(21, @"y")],
    ];
    [self checkSequence:bodies css:Sheet expectReuse:YES];
    [self checkSequence:bodies css:[Sheet stringByAppendingString:SiblingSheet] expectReuse:YES];
}

- (void)testStructuralFrameAfterTemplatePatchesMatchesAFreshParse
{
    NSArray<NSString *> *bodies = @[
        @"<div data-lh-id=\"5\" style=\"width:10%\">a</div><div data-lh-id=\"6\" class=\"x\">b</div>",
        @"<div data-lh-id=\"5\" style=\"width:20%\">a</div><div data-lh-id=\"6\" class=\"x\">b</div>",
        @"<div data-lh-id=\"5\" style=\"width:30%\">changed</div><div data-lh-id=\"6\" class=\"x\">b</div>",
        @"<div data-lh-id=\"5\" style=\"width:30%\">changed</div><div data-lh-id=\"6\" class=\"x on\">b</div>",
        @"<div data-lh-id=\"5\" style=\"width:40%\">again</div><div data-lh-id=\"6\" class=\"x on\">b</div><div data-lh-id=\"7\">new</div>",
    ];
    [self checkSequence:bodies css:Sheet expectReuse:YES];
}

- (void)testInsertedContentIsSanitizedLikeParsedContent
{
    NSString *base     = @"<div data-lh-id=\"5\"><p data-lh-id=\"6\">keep</p></div>";
    NSArray  *hostile  = @[
        @"<p data-lh-id=\"7\" onclick=\"alert(1)\" onload=\"x()\">a</p>",
        @"<script data-lh-id=\"7\">alert(1)</script><p data-lh-id=\"8\">b</p>",
        @"<iframe data-lh-id=\"7\" srcdoc=\"<b>x</b>\" src=\"https://example.com\"></iframe>",
        @"<a data-lh-id=\"7\" href=\"javascript:alert(1)\">link</a><a data-lh-id=\"8\" href=\"https://telegram.org\">ok</a>",
        @"<img data-lh-id=\"7\" src=\"file:///etc/passwd\"><img data-lh-id=\"8\" src=\"https://example.com/x.png\">",
        @"<object data-lh-id=\"7\" data=\"x.swf\"></object><embed data-lh-id=\"8\" src=\"x\">",
        @"<style data-lh-id=\"7\">p{color:red}</style>",
        @"<svg data-lh-id=\"7\" width=\"10\" height=\"10\"><script data-lh-id=\"8\">x</script><a data-lh-id=\"9\" href=\"javascript:1\"><circle data-lh-id=\"10\" cx=\"5\" cy=\"5\" r=\"4\" onclick=\"x\"/></a></svg>",
    ];
    for(NSString *payload in hostile)
    {
        NSString   *html     = Wrap(Sheet, [base stringByReplacingOccurrencesOfString:@"</div>" withString:[payload stringByAppendingString:@"</div>"]]);
        LHDocument *previous = Laid(Wrap(Sheet, base), nil);
        LHDocument *patched  = Laid(html, previous);
        LHDocument *fresh    = Laid(html, nil);
        [self expectTree:patched like:fresh label:payload];
        XCTAssertEqual([patched parentsByAttribute:@"onclick"].count, 0u, @"%@", payload);
        XCTAssertEqualObjects(Pixels(patched), Pixels(fresh), @"%@", payload);
        if([payload hasPrefix:@"<script"] || [payload hasPrefix:@"<iframe"] || [payload hasPrefix:@"<object"])
            XCTAssertNil([patched parentsByAttribute:@"data-lh-id"][@"7"], @"%@", payload);
    }
}

- (void)testInsertsPastTheTreeLimitsFallBackToAFreshParse
{
    NSMutableString *deep = [NSMutableString string];
    for(int i = 0; i < 300; i++)
        [deep appendFormat:@"<div data-lh-id=\"%d\">", 100 + i];
    [deep appendString:@"x"];
    for(int i = 0; i < 300; i++)
        [deep appendString:@"</div>"];
    NSMutableString *wide = [NSMutableString string];
    for(int i = 0; i < 20100; i++)
        [wide appendFormat:@"<i data-lh-id=\"%d\"></i>", 1000 + i];
    for(NSString *payload in @[deep, wide])
    {
        NSString   *html     = Wrap(Sheet, [NSString stringWithFormat:@"<div data-lh-id=\"5\">%@</div>", payload]);
        LHDocument *previous = Laid(Wrap(Sheet, @"<div data-lh-id=\"5\"></div>"), nil);
        LHDocument *patched  = Laid(html, previous);
        LHDocument *fresh    = Laid(html, nil);
        XCTAssertFalse(patched.reusedPrevious);
        XCTAssertEqualObjects([patched parentsByAttribute:@"data-lh-id"], [fresh parentsByAttribute:@"data-lh-id"]);
        NSString   *small = Wrap(Sheet, @"<div data-lh-id=\"5\"><b data-lh-id=\"6\">x</b></div>");
        LHDocument *after = Laid(small, patched);
        XCTAssertEqualObjects(Pixels(after), Pixels(Laid(small, nil)));
    }
}

namespace
{
    struct Node
    {
        int                                id = 0;
        std::string                        tag;
        std::string                        text;
        std::vector<std::string>           classes;
        std::string                        style;
        std::string                        key;
        bool                               hidden = false;
        std::vector<std::unique_ptr<Node>> children;
    };

    void Serialize(const Node& node, std::string& out)
    {
        if(node.tag.empty())
        {
            out += node.text;
            return;
        }
        out += "<" + node.tag + " data-lh-id=\"" + std::to_string(node.id) + "\"";
        if(!node.classes.empty())
        {
            out += " class=\"";
            for(size_t i = 0; i < node.classes.size(); i++)
                out += (i ? " " : "") + node.classes[i];
            out += "\"";
        }
        if(node.hidden)
            out += " hidden=\"\"";
        if(!node.key.empty())
            out += " data-k=\"" + node.key + "\"";
        if(!node.style.empty())
            out += " style=\"" + node.style + "\"";
        out += ">";
        for(const auto& child : node.children)
            Serialize(*child, out);
        out += "</" + node.tag + ">";
    }

    void Collect(Node& node, std::vector<Node*>& out)
    {
        out.push_back(&node);
        for(auto& child : node.children)
            Collect(*child, out);
    }
}

- (void)testRandomStructuralPatchesMatchAFreshParse
{
    const char  *budget   = getenv("TWIDGET_FUZZ_SECONDS");
    const double seconds  = budget ? atof(budget) : 0;
    NSDate      *deadline = [NSDate dateWithTimeIntervalSinceNow:seconds];
    int          applied = 0, frames = 0;
    for(unsigned seed = 1; seed <= 6 || [deadline timeIntervalSinceNow] > 0; seed++)
    {
        @autoreleasepool
        {
            std::mt19937 random(seed);
            NSString    *css   = seed % 2 ? Sheet : [Sheet stringByAppendingString:SiblingSheet];
            const char  *tags[] = {"div", "span", "p", "b", "section", "li", "ul", "button", "a", "table", "tr", "td", "em"};
            const int    tagCount = sizeof(tags) / sizeof(tags[0]);
            const char  *names[] = {"on", "x", "y", "big", "card", "dark", "badge"};
            const char  *words[] = {"alpha", "beta gamma", "δ", "longer words here", "1", " "};
            const char  *styles[] = {"", "padding:3px", "color:#06c", "width:40%", "display:none", "margin-left:5px"};
            int          next = 10;
            Node         root;
            root.tag = "div";
            root.id  = 5;
            std::function<std::unique_ptr<Node>(int)> make = [&](int depth) {
                auto node = std::make_unique<Node>();
                if(depth > 3 || random() % 4 == 0)
                {
                    node->text = words[random() % 6];
                    return node;
                }
                node->tag = tags[random() % tagCount];
                node->id  = next++;
                if(random() % 2)
                    node->classes.push_back(names[random() % 7]);
                int count = random() % 4;
                for(int i = 0; i < count; i++)
                    node->children.push_back(make(depth + 1));
                return node;
            };
            for(int i = 0; i < 4; i++)
                root.children.push_back(make(0));
            auto body = [&]() {
                std::string out;
                Serialize(root, out);
                return Wrap(css, @(out.c_str()));
            };
            LHDocument *document = Laid(body(), nil);
            for(int step = 0; step < 40; step++)
            {
                std::vector<Node*> all;
                Collect(root, all);
                std::vector<Node*> elements;
                for(Node* node : all)
                    if(!node->tag.empty())
                        elements.push_back(node);
                Node* target = elements[random() % elements.size()];
                switch(random() % 9)
                {
                case 8:
                    target->key = random() % 3 ? std::to_string(random() % 3) : "";
                    break;
                case 0:
                {
                    std::string name = names[random() % 7];
                    auto        it   = std::find(target->classes.begin(), target->classes.end(), name);
                    if(it == target->classes.end())
                        target->classes.push_back(name);
                    else
                        target->classes.erase(it);
                    break;
                }
                case 1:
                    target->hidden = !target->hidden;
                    break;
                case 2:
                    target->style = styles[random() % 6];
                    break;
                case 3:
                    target->children.insert(target->children.begin() + (target->children.empty() ? 0 : random() % (target->children.size() + 1)), make(1));
                    break;
                case 4:
                    if(!target->children.empty())
                        target->children.erase(target->children.begin() + random() % target->children.size());
                    break;
                case 5:
                    if(target->children.size() > 1)
                    {
                        auto moved = std::move(target->children.back());
                        target->children.pop_back();
                        target->children.insert(target->children.begin(), std::move(moved));
                    }
                    break;
                case 6:
                    for(auto& child : target->children)
                        if(child->tag.empty())
                            child->text = words[random() % 6];
                    break;
                default:
                    target->style = random() % 2 ? "padding:" + std::to_string(random() % 6) + "px" : "";
                    break;
                }
                NSString   *html  = body();
                LHDocument *patch = Laid(html, document);
                LHDocument *fresh = Laid(html, nil);
                frames++;
                if(patch.reusedPrevious)
                    applied++;
                XCTAssertEqualObjects(Pixels(patch), Pixels(fresh), @"seed %u step %d %@", seed, step, html);
                XCTAssertEqualObjects([patch parentsByAttribute:@"data-lh-id"], [fresh parentsByAttribute:@"data-lh-id"], @"seed %u step %d", seed, step);
                document = patch;
            }
        }
    }
    NSLog(@"STRUCTURE FUZZ frames=%d patched=%d", frames, applied);
    XCTAssertGreaterThan(applied, frames * 3 / 4);
}

@end
