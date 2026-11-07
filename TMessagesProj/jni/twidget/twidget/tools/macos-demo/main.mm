// A macOS demo of TwidgetCore: widget-sandbox's corpus in a scrolling feed of bubbles, each a
// WidgetHost (QuickJS and the validator from widget-sandbox) over the CoreGraphics backend, with
// transform and opacity animations on Core Animation.
//   twidget-macos-demo.app/Contents/MacOS/twidget-macos-demo [corpus folder or .html file]
#import <AppKit/AppKit.h>

#import "widget_view.h"

#include "twidget/resources.h"

#include <algorithm>
#include <memory>
#include <vector>

/// The feed: cards top down.
@interface TWFeedView : NSView
@end

@implementation TWFeedView
- (BOOL)isFlipped {
    return YES;
}
@end

struct Card {
    NSTextField* title;
    TWWidgetView* view;
    NSTextField* status;
};

@interface TWAppDelegate : NSObject <NSApplicationDelegate>
@end

@implementation TWAppDelegate {
    std::shared_ptr<twidget::SandboxResources> _resources;
    NSString* _corpus;
    NSString* _current;
    NSWindow* _window;
    NSPopUpButton* _folders;
    NSButton* _dark;
    NSSlider* _width;
    NSTextField* _widthLabel;
    NSScrollView* _scroll;
    TWFeedView* _feed;
    std::vector<Card> _cards;
}

- (void)applicationDidFinishLaunching:(NSNotification*)notification {
    try {
        _resources = twidget::SandboxResources::fromDirectory(TW_WIDGET_JS);
    } catch (const std::exception& e) {
        NSAlert* alert = [[NSAlert alloc] init];
        alert.messageText = [NSString stringWithFormat:@"widget-sandbox's WidgetJS bundles: %s", e.what()];
        [alert runModal];
        [NSApp terminate:nil];
        return;
    }
    _corpus = @TW_CORPUS;

    _window = [[NSWindow alloc] initWithContentRect:NSMakeRect(0, 0, 820, 960)
                                          styleMask:NSWindowStyleMaskTitled | NSWindowStyleMaskClosable | NSWindowStyleMaskResizable | NSWindowStyleMaskMiniaturizable
                                            backing:NSBackingStoreBuffered
                                              defer:NO];
    _window.title = @"twidget on macOS";

    _folders = [[NSPopUpButton alloc] init];
    NSMutableArray<NSString*>* found = [NSMutableArray array];
    NSDirectoryEnumerator* walk = [NSFileManager.defaultManager enumeratorAtPath:_corpus];
    for (NSString* path in walk) {
        if ([path.pathExtension isEqualToString:@"html"] && ![found containsObject:path.stringByDeletingLastPathComponent])
            [found addObject:path.stringByDeletingLastPathComponent];
    }
    [found sortUsingSelector:@selector(compare:)];
    [_folders addItemsWithTitles:found];
    _folders.target = self;
    _folders.action = @selector(folderChanged:);

    _dark = [NSButton checkboxWithTitle:@"Dark" target:self action:@selector(darkChanged:)];
    _width = [NSSlider sliderWithValue:360 minValue:240 maxValue:640 target:self action:@selector(widthChanged:)];
    _width.continuous = YES;
    [_width.widthAnchor constraintEqualToConstant:160].active = YES;
    _widthLabel = [NSTextField labelWithString:@"360"];
    NSButton* reload = [NSButton buttonWithTitle:@"Reload" target:self action:@selector(reload:)];
    NSStackView* bar = [NSStackView stackViewWithViews:@[ [NSTextField labelWithString:@"Corpus"], _folders, _dark, [NSTextField labelWithString:@"Width"], _width, _widthLabel, reload ]];
    bar.edgeInsets = NSEdgeInsetsMake(8, 12, 8, 12);

    _feed = [[TWFeedView alloc] initWithFrame:NSMakeRect(0, 0, 800, 100)];
    _feed.wantsLayer = YES;
    _scroll = [[NSScrollView alloc] init];
    _scroll.documentView = _feed;
    _scroll.hasVerticalScroller = YES;
    _scroll.contentView.postsBoundsChangedNotifications = YES;
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(scrolled:) name:NSViewBoundsDidChangeNotification object:_scroll.contentView];

    NSView* content = [[NSView alloc] init];
    bar.translatesAutoresizingMaskIntoConstraints = NO;
    _scroll.translatesAutoresizingMaskIntoConstraints = NO;
    [content addSubview:bar];
    [content addSubview:_scroll];
    [NSLayoutConstraint activateConstraints:@[
        [bar.topAnchor constraintEqualToAnchor:content.topAnchor], [bar.leadingAnchor constraintEqualToAnchor:content.leadingAnchor],
        [bar.trailingAnchor constraintLessThanOrEqualToAnchor:content.trailingAnchor], [_scroll.topAnchor constraintEqualToAnchor:bar.bottomAnchor],
        [_scroll.leadingAnchor constraintEqualToAnchor:content.leadingAnchor], [_scroll.trailingAnchor constraintEqualToAnchor:content.trailingAnchor],
        [_scroll.bottomAnchor constraintEqualToAnchor:content.bottomAnchor],
    ]];
    _window.contentView = content;
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(resized:) name:NSWindowDidResizeNotification object:_window];
    [_window layoutIfNeeded];

    // Launched from Xcode, the arguments also carry its own -Option value pairs.
    NSArray<NSString*>* arguments = NSProcessInfo.processInfo.arguments;
    NSString* initial = arguments.count > 1 && ![arguments[1] hasPrefix:@"-"] ? arguments[1] : nil;
    if (initial) {
        [_folders selectItemWithTitle:[initial stringByReplacingOccurrencesOfString:[_corpus stringByAppendingString:@"/"] withString:@""]];
        [self load:initial];
    } else {
        if ([found containsObject:@"common/cards"]) [_folders selectItemWithTitle:@"common/cards"];
        [self load:[_corpus stringByAppendingPathComponent:_folders.titleOfSelectedItem ?: @""]];
    }
    [_window center];
    [_window makeKeyAndOrderFront:nil];
    [NSApp activateIgnoringOtherApps:YES];
}

- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication*)sender {
    return YES;
}

- (void)applicationWillTerminate:(NSNotification*)notification {
    [self clear];
}

- (void)clear {
    // Each view joins its widget's threads as it closes.
    for (Card& card : _cards) {
        [card.view close];
        [card.title removeFromSuperview];
        [card.view removeFromSuperview];
        [card.status removeFromSuperview];
    }
    _cards.clear();
}

- (void)load:(NSString*)path {
    _current = path;
    [self clear];
    NSMutableArray<NSString*>* files = [NSMutableArray array];
    BOOL directory = NO;
    if ([NSFileManager.defaultManager fileExistsAtPath:path isDirectory:&directory] && !directory) {
        [files addObject:path];
    } else {
        for (NSString* name in [[NSFileManager.defaultManager contentsOfDirectoryAtPath:path error:nil] sortedArrayUsingSelector:@selector(compare:)])
            if ([name.pathExtension isEqualToString:@"html"]) [files addObject:[path stringByAppendingPathComponent:name]];
    }

    __weak TWAppDelegate* weakSelf = self;
    for (NSString* file in files) {
        NSData* data = [NSData dataWithContentsOfFile:file];
        if (!data) continue;
        std::string html(static_cast<const char*>(data.bytes), data.length);
        Card card;
        card.title = [NSTextField labelWithString:file.lastPathComponent];
        card.title.font = [NSFont boldSystemFontOfSize:12];
        card.status = [NSTextField labelWithString:@""];
        card.status.font = [NSFont systemFontOfSize:10];
        card.status.textColor = NSColor.secondaryLabelColor;
        card.status.lineBreakMode = NSLineBreakByTruncatingTail;
        // WidgetHost throws when the validator bundle does not load.
        try {
            card.view = [[TWWidgetView alloc] initWithHTML:html dark:_dark.state == NSControlStateValueOn width:_width.doubleValue resources:_resources];
        } catch (const std::exception& e) {
            card.status.stringValue = [NSString stringWithFormat:@"failed: %s", e.what()];
        }
        NSTextField* status = card.status;
        if (card.view) {
            card.view.onChange = ^(NSString* text) {
                status.stringValue = text;
                [weakSelf layoutFeed];
            };
            [_feed addSubview:card.view];
        }
        [_feed addSubview:card.title];
        [_feed addSubview:card.status];
        _cards.push_back(card);
    }
    [self applyTheme];
    [self layoutFeed];
    for (Card& card : _cards) [card.view start];
    _window.title = [NSString stringWithFormat:@"twidget on macOS: %@ (%zu widgets)", path.lastPathComponent, _cards.size()];
}

- (void)layoutFeed {
    CGFloat y = 12, width = std::max<CGFloat>(_scroll.contentSize.width, _width.doubleValue + 32);
    for (Card& card : _cards) {
        card.title.frame = NSMakeRect(16, y, width - 32, 16);
        y += 18;
        if (card.view) {
            card.view.frame = NSMakeRect(16, y, _width.doubleValue, card.view.contentHeight);
            y += card.view.contentHeight + 2;
        }
        card.status.frame = NSMakeRect(16, y, width - 32, 14);
        y += 28;
    }
    [_feed setFrameSize:NSMakeSize(width, std::max<CGFloat>(y, _scroll.contentSize.height))];
    [self scrolled:nil];
}

- (void)applyTheme {
    BOOL dark = _dark.state == NSControlStateValueOn;
    _window.appearance = [NSAppearance appearanceNamed:dark ? NSAppearanceNameDarkAqua : NSAppearanceNameAqua];
    // The chat's bubble colours, under widgets whose own background is transparent.
    _feed.layer.backgroundColor = (dark ? [NSColor colorWithSRGBRed:0x21 / 255.0 green:0x2d / 255.0 blue:0x3b / 255.0 alpha:1]
                                        : [NSColor colorWithSRGBRed:0xef / 255.0 green:0xfd / 255.0 blue:0xde / 255.0 alpha:1]).CGColor;
}

// Off-screen widgets pause: no animation frames, timers at 1 Hz.
- (void)scrolled:(NSNotification*)notification {
    NSRect visible = _scroll.contentView.documentVisibleRect;
    for (Card& card : _cards)
        if (card.view) [card.view setOnScreen:NSIntersectsRect(visible, card.view.frame)];
}

- (void)resized:(NSNotification*)notification {
    [self layoutFeed];
}

- (void)folderChanged:(id)sender {
    [self load:[_corpus stringByAppendingPathComponent:_folders.titleOfSelectedItem]];
}

- (void)reload:(id)sender {
    [self load:_current];
}

- (void)darkChanged:(id)sender {
    [self applyTheme];
    for (Card& card : _cards) [card.view setDark:_dark.state == NSControlStateValueOn];
}

- (void)widthChanged:(id)sender {
    int width = int(_width.doubleValue);
    _widthLabel.stringValue = [NSString stringWithFormat:@"%d", width];
    for (Card& card : _cards) [card.view setContentWidth:width];
    [self layoutFeed];
}

@end

int main(int argc, const char* argv[]) {
    @autoreleasepool {
        NSApplication* app = NSApplication.sharedApplication;
        app.activationPolicy = NSApplicationActivationPolicyRegular;
        TWAppDelegate* delegate = [[TWAppDelegate alloc] init];
        app.delegate = delegate;
        [app run];
    }
    return 0;
}
