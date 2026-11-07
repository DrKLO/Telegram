#import "LHDocument.h"

#ifdef __cplusplus
#include "../core/lhx_painter.h"
#include "LHRenderList.h"

@interface LHDocument (Painting)
- (CGSize)paintWithMaxWidth:(CGFloat)maxWidth clip:(CGRect)clip painter:(lhx::Painter &)painter;
- (CGSize)recordWithMaxWidth:(CGFloat)maxWidth recorder:(lhx::RenderRecorder &)recorder;
@end
#endif
