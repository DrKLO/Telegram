#import <XCTest/XCTest.h>
#import "LHDocument.h"
#include <mach/mach.h>

@interface RebuildMemoryTests : XCTestCase
@end

@implementation RebuildMemoryTests

static double FootprintMegabytes()
{
    task_vm_info_data_t    info;
    mach_msg_type_number_t count = TASK_VM_INFO_COUNT;
    if(task_info(mach_task_self(), TASK_VM_INFO, (task_info_t)&info, &count) != KERN_SUCCESS)
        return 0;
    return info.phys_footprint / 1048576.0;
}

static NSString *RebuildPage(int frame)
{
    NSMutableString *page = [NSMutableString stringWithString:@"<!DOCTYPE html><style>.a{padding:1px}.b{padding:2px}</style><div>"];
    for(int i = 0; i < 300; i++)
        [page appendFormat:@"<p class=\"%@\">row %d</p>", (i + frame) % 2 ? @"a" : @"b", i];
    [page appendString:@"</div>"];
    return page;
}

- (void)testRebuiltRenderTreesAreReleased
{
#if __has_feature(address_sanitizer)
    XCTSkip(@"the sanitizer quarantine holds freed memory");
#endif
    LHDocument *document = [[LHDocument alloc] initWithHTML:RebuildPage(0) css:nil previous:nil];
    [document layoutWithMaxWidth:300 preferredWidth:300];
    for(int frame = 1; frame <= 20; frame++)
    {
        @autoreleasepool
        {
            document = [[LHDocument alloc] initWithHTML:RebuildPage(frame) css:nil previous:document];
            [document layoutWithMaxWidth:300 preferredWidth:300];
        }
    }
    const double start = FootprintMegabytes();
    for(int frame = 21; frame <= 600; frame++)
    {
        @autoreleasepool
        {
            LHDocument *next = [[LHDocument alloc] initWithHTML:RebuildPage(frame) css:nil previous:document];
            XCTAssertTrue(next.reusedPrevious);
            XCTAssertFalse(next.reusedLayout);
            [next layoutWithMaxWidth:300 preferredWidth:300];
            document = next;
        }
    }
    XCTAssertLessThan(FootprintMegabytes() - start, 40.0);
}

@end
