// One widget in the feed: a WidgetHost (QuickJS and the validator from widget-sandbox) over a
// MacRenderer. The document shows as the view's layer contents; the compositor's layers are Core
// Animation layers above it, playing their tracks on the render server.
#pragma once

#import <AppKit/AppKit.h>

#include "twidget/resources.h"

#include <memory>
#include <string>

@interface TWWidgetView : NSView

- (instancetype)initWithHTML:(const std::string&)html
                        dark:(BOOL)dark
                       width:(CGFloat)width
                   resources:(std::shared_ptr<twidget::SandboxResources>)resources;

- (void)start;
- (void)setDark:(BOOL)dark;
- (void)setContentWidth:(CGFloat)width;
- (void)setOnScreen:(BOOL)visible;
/// Stops the widget's threads; nothing calls back after it. Also run on dealloc.
- (void)close;

/// The height the widget asks for, in points.
@property(nonatomic, readonly) CGFloat contentHeight;
/// On the main thread whenever the height or the diagnostics change.
@property(nonatomic, copy) void (^onChange)(NSString* status);

@end
