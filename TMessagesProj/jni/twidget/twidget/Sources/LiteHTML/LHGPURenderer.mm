#import "LHGPUInternal.h"
#import "LHDocument.h"

#include <algorithm>
#include <cmath>
#include <unordered_map>
#include <vector>

static NSString *const kShaders = @R"METAL(
#include <metal_stdlib>
using namespace metal;

struct Solid
{
    float4 rect;
    float4 color;
};

struct SolidOut
{
    float4 position [[position]];
    float4 rect [[flat]];
    float4 color [[flat]];
};

vertex SolidOut solid_vertex(uint vid [[vertex_id]], uint iid [[instance_id]], constant Solid *solids [[buffer(0)]], constant float2 &viewport [[buffer(1)]])
{
    Solid  s      = solids[iid];
    float2 lo     = floor(s.rect.xy);
    float2 hi     = ceil(s.rect.zw);
    float2 corner = float2((vid & 1) ? hi.x : lo.x, (vid & 2) ? hi.y : lo.y);
    SolidOut o;
    o.position = float4(corner.x / viewport.x * 2.0 - 1.0, 1.0 - corner.y / viewport.y * 2.0, 0.0, 1.0);
    o.rect     = s.rect;
    o.color    = s.color;
    return o;
}

fragment float4 solid_fragment(SolidOut in [[stage_in]])
{
    float2 p  = floor(in.position.xy);
    float  cx = clamp(min(in.rect.z, p.x + 1.0) - max(in.rect.x, p.x), 0.0, 1.0);
    float  cy = clamp(min(in.rect.w, p.y + 1.0) - max(in.rect.y, p.y), 0.0, 1.0);
    return in.color * (cx * cy);
}

struct Glyph
{
    float4 rect;
    float4 color;
    float2 uv;
    float2 size;
};

struct GlyphOut
{
    float4 position [[position]];
    float2 origin [[flat]];
    float2 uv [[flat]];
    float2 size [[flat]];
    float4 color [[flat]];
};

vertex GlyphOut glyph_vertex(uint vid [[vertex_id]], uint iid [[instance_id]], constant Glyph *glyphs [[buffer(0)]], constant float2 &viewport [[buffer(1)]])
{
    Glyph  g      = glyphs[iid];
    float2 corner = float2((vid & 1) ? g.rect.z : g.rect.x, (vid & 2) ? g.rect.w : g.rect.y);
    GlyphOut o;
    o.position = float4(corner.x / viewport.x * 2.0 - 1.0, 1.0 - corner.y / viewport.y * 2.0, 0.0, 1.0);
    o.origin   = g.rect.xy;
    o.uv       = g.uv;
    o.size     = g.size;
    o.color    = g.color;
    return o;
}

fragment float4 glyph_fragment(GlyphOut in [[stage_in]], texture2d<float, access::read> atlas [[texture(0)]])
{
    float2 local = floor(in.position.xy - in.origin);
    if(in.size.x > 0.0)
        local = min(local, in.size - 1.0);
    return in.color * atlas.read(uint2(int2(local) + int2(in.uv))).r;
}

struct Quad
{
    float4 rect;
    float2 uv;
    float2 pad;
};

struct QuadOut
{
    float4 position [[position]];
    float2 origin [[flat]];
    float2 uv [[flat]];
};

vertex QuadOut page_vertex(uint vid [[vertex_id]], uint iid [[instance_id]], constant Quad *quads [[buffer(0)]], constant float2 &viewport [[buffer(1)]])
{
    Quad   q      = quads[iid];
    float2 corner = float2((vid & 1) ? q.rect.z : q.rect.x, (vid & 2) ? q.rect.w : q.rect.y);
    QuadOut o;
    o.position = float4(corner.x / viewport.x * 2.0 - 1.0, 1.0 - corner.y / viewport.y * 2.0, 0.0, 1.0);
    o.origin   = q.rect.xy;
    o.uv       = q.uv;
    return o;
}

fragment float4 page_fragment(QuadOut in [[stage_in]], texture2d<float, access::read> page [[texture(0)]])
{
    return page.read(uint2(int2(floor(in.position.xy - in.origin)) + int2(in.uv)));
}

struct TextureOut
{
    float4 position [[position]];
    float2 origin [[flat]];
};

vertex TextureOut texture_vertex(uint vid [[vertex_id]], constant float4 &rect [[buffer(0)]], constant float2 &viewport [[buffer(1)]])
{
    float2 corner = float2((vid & 1) ? rect.z : rect.x, (vid & 2) ? rect.w : rect.y);
    TextureOut o;
    o.position = float4(corner.x / viewport.x * 2.0 - 1.0, 1.0 - corner.y / viewport.y * 2.0, 0.0, 1.0);
    o.origin   = rect.xy;
    return o;
}

fragment float4 texture_fragment(TextureOut in [[stage_in]], texture2d<float, access::read> tex [[texture(0)]])
{
    int2 p = int2(floor(in.position.xy - in.origin));
    if(p.x < 0 || p.y < 0 || p.x >= int(tex.get_width()) || p.y >= int(tex.get_height()))
        return float4(0.0);
    return tex.read(uint2(p));
}
)METAL";

namespace
{
struct CachedTexture
{
    id<MTLTexture> texture;
    CFAbsoluteTime used;
    size_t         bytes;
    int            page;
    uint16_t       x, y;
};

struct QuadInstance
{
    float rect[4];
    float uv[2];
    float pad[2];
};

struct Page
{
    id<MTLBuffer>  buffer;
    id<MTLTexture> texture;
    size_t         bytesPerRow;
    int            shelfX, shelfY, shelfH;
};

const int kPageSize  = 1024;
const int kMaxPages  = 8;

struct SolidInstance
{
    float rect[4];
    float color[4];
};

struct GlyphInstance
{
    float rect[4];
    float color[4];
    float uv[2];
    float size[2];
};

struct GlyphKey
{
    const void *font;
    CGGlyph     glyph;
    int32_t     fx, fy;
    bool operator==(const GlyphKey &o) const { return font == o.font && glyph == o.glyph && fx == o.fx && fy == o.fy; }
};

struct GlyphKeyHash
{
    size_t operator()(const GlyphKey &k) const
    {
        return std::hash<const void *>()(k.font) ^ (size_t(k.glyph) * 0x9e3779b97f4a7c15ULL) ^ (size_t(k.fx) << 20) ^ (size_t(k.fy) << 40);
    }
};

struct GlyphEntry
{
    int32_t  dx, dy;
    uint16_t w, h, u, v;
    bool     empty;
};

const int kAtlasSize = 2048;
}

@implementation LHGPURenderer
{
    id<MTLRenderPipelineState>                    _solid;
    id<MTLRenderPipelineState>                    _texture;
    id<MTLRenderPipelineState>                    _glyph;
    id<MTLRenderPipelineState>                    _page;
    std::vector<Page>                             _pages;
    BOOL                                          _pagesFull;
    CGColorSpaceRef                               _space;
    id<MTLTexture>                                _atlas;
    std::unordered_map<GlyphKey, GlyphEntry, GlyphKeyHash> _glyphs;
    std::unordered_map<uint64_t, GlyphEntry>      _masks;
    std::vector<CFTypeRef>                        _atlasFonts;
    int                                           _shelfX, _shelfY, _shelfH;
    BOOL                                          _atlasFull;
    NSUInteger                                    _rasterizedGlyphs;
    std::unordered_map<uint64_t, CachedTexture> _textures;
    uint64_t                                      _frame;
    CFAbsoluteTime                                _now;
    size_t                                        _bytes;
    NSUInteger                                    _captured;
    MTLStorageMode                                _storage;
}

+ (NSMutableDictionary<NSNumber *, NSArray *> *)pipelineCache
{
    static NSMutableDictionary *cache;
    static dispatch_once_t      once;
    dispatch_once(&once, ^{
        cache = [NSMutableDictionary dictionary];
    });
    return cache;
}

- (instancetype)initWithDevice:(id<MTLDevice>)device
{
    if((self = [super init]))
    {
        _device = device ?: MTLCreateSystemDefaultDevice();
        if(!_device)
            return nil;
        _queue   = [_device newCommandQueue];
        _storage = MTLStorageModeManaged;
        if(@available(macOS 10.15, *))
        {
            if(_device.hasUnifiedMemory)
                _storage = MTLStorageModeShared;
        }
        NSArray *pipelines;
        @synchronized(LHGPURenderer.class)
        {
            NSNumber *key = @(_device.registryID);
            pipelines     = LHGPURenderer.pipelineCache[key];
            if(!pipelines)
            {
                NSError         *error   = nil;
                id<MTLLibrary>   library = [_device newLibraryWithSource:kShaders options:nil error:&error];
                if(!library)
                {
                    NSLog(@"LHGPURenderer: %@", error);
                    return nil;
                }
                id<MTLRenderPipelineState> solid   = [self pipelineWithLibrary:library vertex:@"solid_vertex" fragment:@"solid_fragment"];
                id<MTLRenderPipelineState> texture = [self pipelineWithLibrary:library vertex:@"texture_vertex" fragment:@"texture_fragment"];
                id<MTLRenderPipelineState> glyph   = [self pipelineWithLibrary:library vertex:@"glyph_vertex" fragment:@"glyph_fragment"];
                id<MTLRenderPipelineState> page    = [self pipelineWithLibrary:library vertex:@"page_vertex" fragment:@"page_fragment"];
                if(!solid || !texture || !glyph || !page)
                    return nil;
                pipelines                         = @[solid, texture, glyph, page];
                LHGPURenderer.pipelineCache[key] = pipelines;
            }
        }
        _solid   = pipelines[0];
        _texture = pipelines[1];
        _glyph   = pipelines[2];
        _page    = pipelines[3];
        MTLTextureDescriptor *desc = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatR8Unorm width:kAtlasSize height:kAtlasSize mipmapped:NO];
        desc.usage                 = MTLTextureUsageShaderRead;
        desc.storageMode           = _storage;
        _atlas                     = [_device newTextureWithDescriptor:desc];
        if(!_atlas)
            return nil;
    }
    return self;
}

- (id<MTLRenderPipelineState>)pipelineWithLibrary:(id<MTLLibrary>)library vertex:(NSString *)vertex fragment:(NSString *)fragment
{
    MTLRenderPipelineDescriptor *desc                   = [MTLRenderPipelineDescriptor new];
    desc.vertexFunction                                 = [library newFunctionWithName:vertex];
    desc.fragmentFunction                               = [library newFunctionWithName:fragment];
    desc.colorAttachments[0].pixelFormat                = MTLPixelFormatBGRA8Unorm;
    desc.colorAttachments[0].blendingEnabled            = YES;
    desc.colorAttachments[0].rgbBlendOperation          = MTLBlendOperationAdd;
    desc.colorAttachments[0].alphaBlendOperation        = MTLBlendOperationAdd;
    desc.colorAttachments[0].sourceRGBBlendFactor       = MTLBlendFactorOne;
    desc.colorAttachments[0].sourceAlphaBlendFactor     = MTLBlendFactorOne;
    desc.colorAttachments[0].destinationRGBBlendFactor  = MTLBlendFactorOneMinusSourceAlpha;
    desc.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
    NSError *error = nil;
    id<MTLRenderPipelineState> state = [_device newRenderPipelineStateWithDescriptor:desc error:&error];
    if(!state)
        NSLog(@"LHGPURenderer: %@", error);
    return state;
}

- (void)beginRecordingWithScale:(CGFloat)scale space:(CGColorSpaceRef)space recorder:(LHGPURecorder *)recorder
{
    _frame++;
    _now = CFAbsoluteTimeGetCurrent();
    _captured            = 0;
    recorder->scale      = scale;
    recorder->space      = space;
    __unsafe_unretained LHGPURenderer *unsafeSelf = self;
    recorder->cached = [unsafeSelf](uint64_t key) {
        auto it = unsafeSelf->_textures.find(key);
        if(it == unsafeSelf->_textures.end())
            return false;
        it->second.used = unsafeSelf->_now;
        return true;
    };
    recorder->store = [unsafeSelf](uint64_t key, CGContextRef ctx, CGRect device) {
        [unsafeSelf storeKey:key context:ctx device:device];
    };
    if(_space != space)
    {
        CGColorSpaceRelease(_space);
        _space = CGColorSpaceRetain(space);
    }
    recorder->allocate = [unsafeSelf](uint64_t key, CGRect device) {
        return [unsafeSelf contextForKey:key device:device];
    };
    const CGFloat glyphScale = scale;
    recorder->mask = [unsafeSelf, glyphScale](uint64_t key, CGRect box, const std::function<void(CGContextRef)> &draw, LHGlyphSlot &slot) {
        return [unsafeSelf maskSlotForKey:key box:box scale:glyphScale draw:draw slot:&slot];
    };
    recorder->glyph = [unsafeSelf, glyphScale](CTFontRef font, CGGlyph glyph, CGPoint device, LHGlyphSlot &slot) {
        return [unsafeSelf glyphSlotForFont:font glyph:glyph device:device scale:glyphScale slot:&slot];
    };
}

- (void)resetAtlas
{
    _glyphs.clear();
    _masks.clear();
    for(CFTypeRef font : _atlasFonts)
        CFRelease(font);
    _atlasFonts.clear();
    _shelfX = _shelfY = _shelfH = 0;
    _atlasFull = NO;
}

- (BOOL)reserveAtlasWidth:(int)w height:(int)h x:(int *)x y:(int *)y
{
    if(_atlasFull)
        return NO;
    if(_shelfX + w > kAtlasSize)
    {
        _shelfX = 0;
        _shelfY += _shelfH;
        _shelfH = 0;
    }
    if(_shelfY + h > kAtlasSize)
    {
        _atlasFull = YES;
        return NO;
    }
    *x = _shelfX;
    *y = _shelfY;
    _shelfX += w;
    _shelfH = std::max(_shelfH, h);
    return YES;
}

- (BOOL)maskSlotForKey:(uint64_t)key box:(CGRect)box scale:(CGFloat)scale draw:(const std::function<void(CGContextRef)> &)draw slot:(LHGlyphSlot *)slot
{
    auto it = _masks.find(key);
    if(it == _masks.end())
    {
        lhx::AlphaBitmap bitmap;
        if(box.size.width <= 0 || box.size.height <= 0 || box.size.width > 512 || box.size.height > 512)
            return NO;
        int x = 0, y = 0;
        if(![self reserveAtlasWidth:int(box.size.width) height:int(box.size.height) x:&x y:&y])
            return NO;
        if(!lhx::rasterizeMask(box, scale, 512, draw, bitmap))
            return NO;
        [_atlas replaceRegion:MTLRegionMake2D(x, y, bitmap.width, bitmap.height) mipmapLevel:0 withBytes:bitmap.pixels.data() bytesPerRow:bitmap.width];
        it = _masks.emplace(key, GlyphEntry{0, 0, uint16_t(bitmap.width), uint16_t(bitmap.height), uint16_t(x), uint16_t(y), false}).first;
    }
    slot->empty  = false;
    slot->u      = it->second.u;
    slot->v      = it->second.v;
    slot->device = CGRectMake(0, 0, it->second.w, it->second.h);
    return YES;
}

- (BOOL)glyphSlotForFont:(CTFontRef)font glyph:(CGGlyph)glyph device:(CGPoint)device scale:(CGFloat)scale slot:(LHGlyphSlot *)slot
{
    const CGFloat ix = std::floor(device.x), iy = std::floor(device.y);
    GlyphKey      key{font, glyph, int32_t(std::lround((device.x - ix) * 256)), int32_t(std::lround((device.y - iy) * 256))};
    auto          it = _glyphs.find(key);
    if(it == _glyphs.end())
    {
        if(_atlasFull)
            return NO;
        lhx::AlphaBitmap bitmap;
        if(!lhx::rasterizeGlyph(font, glyph, device, scale, 512, bitmap))
            return NO;
        GlyphEntry entry{0, 0, 0, 0, 0, 0, true};
        if(!bitmap.empty)
        {
            int x = 0, y = 0;
            if(![self reserveAtlasWidth:bitmap.width height:bitmap.height x:&x y:&y])
                return NO;
            [_atlas replaceRegion:MTLRegionMake2D(x, y, bitmap.width, bitmap.height) mipmapLevel:0 withBytes:bitmap.pixels.data() bytesPerRow:bitmap.width];
            entry = {bitmap.x, bitmap.y, uint16_t(bitmap.width), uint16_t(bitmap.height), uint16_t(x), uint16_t(y), false};
            _rasterizedGlyphs++;
            if(std::find(_atlasFonts.begin(), _atlasFonts.end(), (CFTypeRef)font) == _atlasFonts.end())
                _atlasFonts.push_back(CFRetain(font));
        }
        it = _glyphs.emplace(key, entry).first;
    }
    const GlyphEntry &e = it->second;
    slot->empty  = e.empty;
    slot->u      = e.u;
    slot->v      = e.v;
    slot->device = CGRectMake(ix + e.dx, iy + e.dy, e.w, e.h);
    return YES;
}

- (BOOL)addPage
{
    if(_pages.size() >= kMaxPages)
        return NO;
    NSUInteger align = 256;
    if(@available(macOS 10.13, *))
        align = MAX(align, [_device minimumLinearTextureAlignmentForPixelFormat:MTLPixelFormatBGRA8Unorm]);
    const size_t bytesPerRow = (kPageSize * 4 + align - 1) / align * align;
    id<MTLBuffer> buffer     = [_device newBufferWithLength:bytesPerRow * kPageSize options:_storage == MTLStorageModeShared ? MTLResourceStorageModeShared : MTLResourceStorageModeManaged];
    if(!buffer)
        return NO;
    MTLTextureDescriptor *desc = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm width:kPageSize height:kPageSize mipmapped:NO];
    desc.usage                 = MTLTextureUsageShaderRead;
    desc.storageMode           = buffer.storageMode;
    id<MTLTexture> texture     = [buffer newTextureWithDescriptor:desc offset:0 bytesPerRow:bytesPerRow];
    if(!texture)
        return NO;
    _pages.push_back({buffer, texture, bytesPerRow, 0, 0, 0});
    _bytes += bytesPerRow * kPageSize;
    return YES;
}

- (CGContextRef)contextForKey:(uint64_t)key device:(CGRect)device CF_RETURNS_RETAINED
{
    const int w = int(device.size.width), h = int(device.size.height);
    if(w <= kPageSize / 2 && h <= kPageSize / 2 && !_pagesFull)
    {
        for(int attempt = 0; attempt < 2; attempt++)
        {
            for(size_t i = 0; i < _pages.size(); i++)
            {
                Page &p = _pages[i];
                if(p.shelfX + w > kPageSize)
                {
                    p.shelfX = 0;
                    p.shelfY += p.shelfH;
                    p.shelfH = 0;
                }
                if(p.shelfY + h > kPageSize)
                    continue;
                uint8_t     *base = static_cast<uint8_t *>(p.buffer.contents) + size_t(p.shelfY) * p.bytesPerRow + size_t(p.shelfX) * 4;
                CGContextRef ctx  = CGBitmapContextCreate(base, w, h, 8, p.bytesPerRow, _space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
                if(!ctx)
                    return nullptr;
                CGContextClearRect(ctx, CGRectMake(0, 0, w, h));
                auto it = _textures.find(key);
                if(it != _textures.end())
                    _textures.erase(it);
                _textures[key] = {nil, _now, 0, int(i), uint16_t(p.shelfX), uint16_t(p.shelfY)};
                p.shelfX += w;
                p.shelfH = std::max(p.shelfH, h);
                _captured++;
                return ctx;
            }
            if(![self addPage])
            {
                _pagesFull = YES;
                break;
            }
        }
    }
    return CGBitmapContextCreate(nullptr, w, h, 8, 0, _space, kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
}

- (void)storeKey:(uint64_t)key context:(CGContextRef)ctx device:(CGRect)device
{
    auto existing = _textures.find(key);
    if(existing != _textures.end() && existing->second.page >= 0)
    {
        if(_storage == MTLStorageModeManaged)
        {
            const Page &p = _pages[existing->second.page];
            NSRange     r = NSMakeRange(size_t(existing->second.y) * p.bytesPerRow, size_t(device.size.height) * p.bytesPerRow);
            [p.buffer didModifyRange:r];
        }
        return;
    }
    const size_t w = CGBitmapContextGetWidth(ctx), h = CGBitmapContextGetHeight(ctx);
    void        *data = CGBitmapContextGetData(ctx);
    if(!data || w == 0 || h == 0)
        return;
    MTLTextureDescriptor *desc = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm width:w height:h mipmapped:NO];
    desc.usage                 = MTLTextureUsageShaderRead;
    desc.storageMode           = _storage;
    id<MTLTexture> texture     = [_device newTextureWithDescriptor:desc];
    if(!texture)
        return;
    [texture replaceRegion:MTLRegionMake2D(0, 0, w, h) mipmapLevel:0 withBytes:data bytesPerRow:CGBitmapContextGetBytesPerRow(ctx)];
    auto it = _textures.find(key);
    if(it != _textures.end())
        _bytes -= it->second.bytes;
    _textures[key] = {texture, _now, w * h * 4, -1, 0, 0};
    _bytes += w * h * 4;
    _captured++;
}

- (BOOL)encodeDisplayList:(LHDisplayList *)list tiles:(NSArray<NSValue *> *)tiles scale:(CGFloat)scale targets:(NSArray<id<MTLTexture>> *)targets commandBuffer:(id<MTLCommandBuffer>)commandBuffer
{
    const std::vector<LHGPUOp> *ops = list.gpuOps;
    if(!ops || list.gpuUnsupported || tiles.count != targets.count)
        return NO;
    std::vector<SolidInstance> solids;
    std::vector<GlyphInstance> glyphs;
    std::vector<QuadInstance>  quads;
    int                        quadPage = -1;
    for(NSUInteger t = 0; t < tiles.count; t++)
    {
        CGRect         tile   = tiles[t].rectValue;
        id<MTLTexture> target = targets[t];
        const CGFloat  ox = std::round(tile.origin.x * scale), oy = std::round(tile.origin.y * scale);
        const CGRect   area = CGRectMake(ox, oy, target.width, target.height);

        MTLRenderPassDescriptor *pass         = [MTLRenderPassDescriptor renderPassDescriptor];
        pass.colorAttachments[0].texture     = target;
        pass.colorAttachments[0].loadAction  = MTLLoadActionClear;
        pass.colorAttachments[0].storeAction = MTLStoreActionStore;
        pass.colorAttachments[0].clearColor  = MTLClearColorMake(0, 0, 0, 0);
        id<MTLRenderCommandEncoder> encoder  = [commandBuffer renderCommandEncoderWithDescriptor:pass];
        if(!encoder)
            return NO;
        const float viewport[2] = {float(target.width), float(target.height)};
        [encoder setVertexBytes:viewport length:sizeof(viewport) atIndex:1];

        auto flushGlyphs = [&]() {
            if(glyphs.empty())
                return;
            [encoder setRenderPipelineState:_glyph];
            [encoder setFragmentTexture:_atlas atIndex:0];
            size_t done = 0;
            while(done < glyphs.size())
            {
                size_t count = std::min<size_t>(glyphs.size() - done, 4096 / sizeof(GlyphInstance));
                [encoder setVertexBytes:glyphs.data() + done length:count * sizeof(GlyphInstance) atIndex:0];
                [encoder drawPrimitives:MTLPrimitiveTypeTriangleStrip vertexStart:0 vertexCount:4 instanceCount:count];
                done += count;
            }
            glyphs.clear();
        };
        auto flushQuads = [&]() {
            if(quads.empty())
                return;
            [encoder setRenderPipelineState:_page];
            [encoder setFragmentTexture:_pages[quadPage].texture atIndex:0];
            size_t done = 0;
            while(done < quads.size())
            {
                size_t count = std::min<size_t>(quads.size() - done, 4096 / sizeof(QuadInstance));
                [encoder setVertexBytes:quads.data() + done length:count * sizeof(QuadInstance) atIndex:0];
                [encoder drawPrimitives:MTLPrimitiveTypeTriangleStrip vertexStart:0 vertexCount:4 instanceCount:count];
                done += count;
            }
            quads.clear();
        };
        auto flushSolids = [&]() {
            if(solids.empty())
                return;
            [encoder setRenderPipelineState:_solid];
            size_t done = 0;
            while(done < solids.size())
            {
                size_t count = std::min<size_t>(solids.size() - done, 4096 / sizeof(SolidInstance));
                [encoder setVertexBytes:solids.data() + done length:count * sizeof(SolidInstance) atIndex:0];
                [encoder drawPrimitives:MTLPrimitiveTypeTriangleStrip vertexStart:0 vertexCount:4 instanceCount:count];
                done += count;
            }
            solids.clear();
        };
        bool textureState = false;
        for(const auto &op : *ops)
        {
            if(!CGRectIntersectsRect(op.device, area))
                continue;
            if(op.kind == LHGPUOp::Mask)
            {
                flushSolids();
                flushQuads();
                GlyphInstance g;
                g.rect[0]  = float(op.device.origin.x - ox);
                g.rect[1]  = float(op.device.origin.y - oy);
                g.rect[2]  = float(CGRectGetMaxX(op.device) - ox);
                g.rect[3]  = float(CGRectGetMaxY(op.device) - oy);
                g.color[0] = op.color[2] / 255.0f;
                g.color[1] = op.color[1] / 255.0f;
                g.color[2] = op.color[0] / 255.0f;
                g.color[3] = op.color[3] / 255.0f;
                g.uv[0]    = float(op.key & 0xffff);
                g.uv[1]    = float((op.key >> 16) & 0xffff);
                g.size[0]  = float((op.key >> 32) & 0xffff);
                g.size[1]  = float((op.key >> 48) & 0xffff);
                glyphs.push_back(g);
                textureState = false;
                continue;
            }
            if(op.kind == LHGPUOp::Fill)
            {
                flushGlyphs();
                flushQuads();
                SolidInstance s;
                s.rect[0]  = float(op.device.origin.x - ox);
                s.rect[1]  = float(op.device.origin.y - oy);
                s.rect[2]  = float(CGRectGetMaxX(op.device) - ox);
                s.rect[3]  = float(CGRectGetMaxY(op.device) - oy);
                s.color[0] = op.color[2] / 255.0f;
                s.color[1] = op.color[1] / 255.0f;
                s.color[2] = op.color[0] / 255.0f;
                s.color[3] = op.color[3] / 255.0f;
                solids.push_back(s);
                textureState = false;
                continue;
            }
            auto it = _textures.find(op.key);
            if(it == _textures.end())
                continue;
            flushSolids();
            flushGlyphs();
            if(it->second.page >= 0)
            {
                if(quadPage != it->second.page)
                {
                    flushQuads();
                    quadPage = it->second.page;
                }
                QuadInstance q;
                q.rect[0] = float(op.device.origin.x - ox);
                q.rect[1] = float(op.device.origin.y - oy);
                q.rect[2] = float(CGRectGetMaxX(op.device) - ox);
                q.rect[3] = float(CGRectGetMaxY(op.device) - oy);
                q.uv[0]   = it->second.x;
                q.uv[1]   = it->second.y;
                quads.push_back(q);
                textureState = false;
                continue;
            }
            flushQuads();
            if(!textureState)
            {
                [encoder setRenderPipelineState:_texture];
                [encoder setVertexBytes:viewport length:sizeof(viewport) atIndex:1];
                textureState = true;
            }
            const float rect[4] = {float(op.device.origin.x - ox), float(op.device.origin.y - oy), float(CGRectGetMaxX(op.device) - ox), float(CGRectGetMaxY(op.device) - oy)};
            [encoder setVertexBytes:rect length:sizeof(rect) atIndex:0];
            [encoder setFragmentTexture:it->second.texture atIndex:0];
            [encoder drawPrimitives:MTLPrimitiveTypeTriangleStrip vertexStart:0 vertexCount:4];
        }
        flushSolids();
        flushGlyphs();
        flushQuads();
        [encoder endEncoding];
    }
    return YES;
}

- (void)endFrame
{
    if(_atlasFull || _shelfY > kAtlasSize * 7 / 8)
        [self resetAtlas];
    size_t used = 0;
    for(const Page &p : _pages)
        used += size_t(p.shelfY + p.shelfH) * kPageSize;
    if(_pagesFull || (_pages.size() >= kMaxPages && used > size_t(kMaxPages) * kPageSize * kPageSize * 7 / 8))
    {
        for(auto it = _textures.begin(); it != _textures.end();)
        {
            if(it->second.page >= 0)
                it = _textures.erase(it);
            else
                ++it;
        }
        for(Page &p : _pages)
            p.shelfX = p.shelfY = p.shelfH = 0;
        _pagesFull = NO;
    }
    const CFAbsoluteTime keep = 2;
    size_t               dedicated = 0;
    for(auto it = _textures.begin(); it != _textures.end();)
    {
        if(_now - it->second.used > keep)
        {
            _bytes -= it->second.bytes;
            it = _textures.erase(it);
        } else
        {
            dedicated += it->second.bytes;
            ++it;
        }
    }
    const size_t budget = 32 << 20;
    if(dedicated > budget)
    {
        std::vector<std::pair<CFAbsoluteTime, uint64_t>> order;
        for(const auto &entry : _textures)
        {
            if(entry.second.page < 0)
                order.emplace_back(entry.second.used, entry.first);
        }
        std::sort(order.begin(), order.end());
        for(const auto &item : order)
        {
            if(dedicated <= budget * 3 / 4)
                break;
            auto it = _textures.find(item.second);
            dedicated -= it->second.bytes;
            _bytes -= it->second.bytes;
            _textures.erase(it);
        }
    }
}

- (NSUInteger)textureBytes
{
    return _bytes;
}

- (NSUInteger)textureCount
{
    return _textures.size();
}

- (NSUInteger)capturedInLastFrame
{
    return _captured;
}

- (NSUInteger)rasterizedGlyphs
{
    return _rasterizedGlyphs;
}

- (void)dealloc
{
    [self resetAtlas];
    CGColorSpaceRelease(_space);
}

@end
