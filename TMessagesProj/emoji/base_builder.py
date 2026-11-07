#!/usr/bin/env python3
"""Build an EPK3/v2 pack. getEmoji() in EmojiPack.java returns complete RGBA.

Inputs: the original PNG-based emoji.pack and original 64x64 texture ZIP.
The existing mask decomposition is a build input, never part of the public API.
Use --lossless to disable fitted palettes and lossy WebP candidates.
"""
import argparse, hashlib, io, json, pickle, struct, time, zipfile, zlib
from pathlib import Path
from collections import Counter
import numpy as np
from scipy.ndimage import uniform_filter
from PIL import Image, features
import optimizer_core as old
import optimizer_visual as visual

def custom(i,codec,pred,parents,ts,data,pal=None,approx=False):
 if pal is None:o=old.option(i,codec,pred,parents,data)
 else:
  flags=0;pb=pal.astype(np.uint8).tobytes()
  if all(v==((v>>1)*2+(v>>7)) for v in pb):pb=old.pack7(pb);flags=1
  o=dict(i=i,codec=codec,pred=pred,parents=parents,data=pb+data,colors=len(pal),channels=3,flags=flags,paletteLength=len(pb),cost=len(pb)+len(data))
 o['flags'] |= ((ts[0] if ts else 0)<<1)|((ts[1] if len(ts)>1 else 0)<<4)|(128 if approx else 0)
 o['transforms']=ts;o['approx']=approx
 return o
def pixels(o):
 i=o['i'];b=o['data'];ch=o['channels'];n=o['colors'];plen=o['paletteLength']
 if o['codec']==3:a=np.array(Image.open(io.BytesIO(b)).convert('RGBA'))
 else:
  if o['flags']&1:
   vals=[]
   for j in range(n*ch):
    bit=j*7;p=bit>>3;s=bit&7;v=b[p]>>s
    if s>1:v|=b[p+1]<<(8-s)
    v&=127;vals.append(v*2+(v>>6))
   pal=np.array(vals,np.uint8).reshape(n,ch)
  else:pal=np.frombuffer(b[:plen],np.uint8).reshape(n,ch)
  maps=[visual.trans(R[p]['idx'],t) for p,t in zip(o['parents'],o['transforms'])]
  if o['codec']==4:idx=maps[0]
  else:
   lens=[len(R[p]['pal']) for p in o['parents']];raw=zlib.decompress(b[plen:],-15);lut=np.frombuffer(raw[:sum(lens)],np.uint8)
   lm=lut[:lens[0]][maps[0]][:,:32];rm=lut[lens[0]:][maps[1]][:,32:];idx=np.concatenate([lm,rm],axis=1)
  a=np.empty((64,64,4),np.uint8);a[:,:,:3]=pal[idx];a[:,:,3]=255
 if R[i]['mask']!=65535:a[:,:,3]=BASE[i,:,:,3]
 return a


def assemble(base_half, mirrors, templates, half_templates, native, lossless=False):
    roots, ind, edges = old.prepare()
    for i, options in enumerate(base_half):
        for _, a, b, codec, data in options:
            edges[i].append(custom(i, codec, 3, (a, b), (0, 0), data[2:-4] if codec == 0 else data))
            if i < NE and R[i]['mask'] != 65535:
                ln = len(R[a]['pal']) + len(R[b]['pal'])
                if codec == 0:
                    raw = visual.zlib.decompress(data); lut = raw[:ln]
                    delta = np.frombuffer(raw[ln:], np.uint8).reshape(64, 64).copy()
                else:
                    lut = data[:ln]
                    delta = np.array(Image.open(io.BytesIO(data[ln:])).convert('L'))
                delta[BASE[i, :, :, 3] == 0] = 0
                zz = visual.zlib.compress(lut + delta.tobytes(), 9)[2:-4]
                edges[i].append(custom(i, 0, 3, (a, b), (0, 0), zz))
    for i, options in enumerate(mirrors):
        for o in options:
            j, t, pred = o['parent'], o['transform'], o['pred']
            edges[i].append(custom(i, 0, pred, (j,), (t,), o['zlib'][2:-4]))
            edges[i].append(custom(i, 1, pred, (j,), (t,), o['lut'] + o['webp']))
    if not lossless:
        for i, (options, _) in enumerate(templates):
            for _, j, t, pal, quality in options:
                o = custom(i, 4, 4, (j,), (t,), b'', pal, True)
                o['quality'] = quality; edges[i].append(o)
        for i, options in enumerate(half_templates):
            if not options: continue
            ref = visual.metrics(BASE[i], O[i])
            for _, a, b, ta, tb, pal, zz, lm, rm in options:
                o = custom(i, 0, 3, (a, b), (ta, tb), zz[2:-4], pal, True)
                if visual.passes_global(pixels(o), O[i], ref): edges[i].append(o)
        for i, (choice, ref, attempts) in enumerate(native):
            options = [o for o in edges[i] if o.get('approx')]
            if choice:
                q, b, quality = choice
                o = old.option(i, 3, 0, (), b)
                o.update(approx=True, flags=128, native_webp_quality=q, quality=quality)
                options.append(o)
            if not options: continue
            e0 = uniform_filter(visual.error(BASE[i], O[i]), size=5)
            s0 = visual.structure(BASE[i], O[i])
            kept = [o for o in edges[i] if not o.get('approx')]
            for o in options:
                if visual.passes_local(pixels(o), O[i], e0, s0):
                    if 'native_webp_quality' in o:
                        if o['cost'] < ind[i]['cost']: ind[i] = o
                    else: kept.append(o)
            edges[i] = kept
    return old.select(roots, ind, edges, False)


def main():
    global R, O, NE, N, BASE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', required=True, help='Original PNG-based pack, not the EPK3 result')
    parser.add_argument('--textures', required=True)
    parser.add_argument('--output', default='emoji.pack')
    parser.add_argument('--workers', type=int, default=4)
    parser.add_argument('--cache', help='Optional trusted directory for resumable intermediate stages')
    parser.add_argument('--lossless', action='store_true', help='Keep the old visual result exactly; hidden RGB may differ')
    args = parser.parse_args()
    if args.workers < 1: parser.error('--workers must be positive')
    if Path(args.reference).resolve() == Path(args.output).resolve(): parser.error('Use a separate output path')
    start = time.monotonic()
    R, NE, lookup, original_size = old.read_reference(args.reference, args.textures)
    N = len(R)
    with zipfile.ZipFile(args.textures) as archive:
        O = np.array([np.array(Image.open(archive.open(f"{d['id']//4096}_{d['id']%4096}.png")).convert('RGBA')) for d in R[:NE]])
    old.R = R; old.N = N; old.NE = NE; old.MASK_LOOKUP = lookup
    old.IDX = old.I = np.array([d['idx'] for d in R])
    visual.initialize(R, O); BASE = visual.RGBA
    fingerprint = hashlib.sha256()
    for f in [Path(args.reference), Path(args.textures), Path(__file__), Path(old.__file__), Path(visual.__file__)]:
        fingerprint.update(f.read_bytes())
    fingerprint.update((Image.__version__ + str(features.version('webp'))).encode())
    key = fingerprint.hexdigest()
    cache = Path(args.cache) if args.cache else None
    if cache: cache.mkdir(parents=True, exist_ok=True)
    def stage(name, fn, jobs):
        path = cache / (name + '.pkl') if cache else None
        if path and path.exists():
            # This cache is produced by this tool. Do not use untrusted pickle files.
            with path.open('rb') as stream: saved = pickle.load(stream)
            if saved['key'] == key:
                print(name + ': cache', flush=True); return saved['value']
        value = old.map_jobs(fn, jobs, args.workers, name)
        if path:
            temp = path.with_suffix('.tmp')
            with temp.open('wb') as stream: pickle.dump({'key': key, 'value': value}, stream, protocol=5)
            temp.replace(path)
        return value
    old.B = stage('base-webp', old.baseline, R)
    old.C = old.candidates()
    old.D = stage('base-delta', old.encode_delta, range(N))
    old.W = stage('base-delta-webp', old.encode_delta_webp, range(N))
    old.features = old.half_candidates()
    base_half = stage('base-halves', old.encode_half, range(N))
    visual.make_candidates(); visual.D = old.D
    mirrors = stage('mirrors', visual.mirror_job, range(N))
    templates = half_templates = native = []
    if not args.lossless:
        templates = stage('fitted-palettes', visual.template_job, range(NE))
        visual.make_half_candidates()
        half_templates = stage('fitted-halves', visual.half_template_job, range(NE))
        native = stage('native-webp', visual.native_webp_job, range(NE))
    selected = assemble(base_half, mirrors, templates, half_templates, native, args.lossless)
    output = Path(args.output); output.parent.mkdir(parents=True, exist_ok=True)
    data = bytearray(old.pack(selected, output)); struct.pack_into('<H', data, 4, 2)
    output.write_bytes(data)
    stats = dict(reference_bytes=original_size, previous_pack_bytes=3670504, pack_bytes=len(data),
                 saving_percent=100*(1-len(data)/original_size),
                 emoji_count=NE, mask_count=N-NE,
                 fitted_records=sum(o.get('approx',False) and 'native_webp_quality' not in o for o in selected),
                 native_lossy_records=sum('native_webp_quality' in o for o in selected),
                 horizontal_mirror_references=sum(t==1 for o in selected for t in o.get('transforms',())),
                 transformed_references=sum(t!=0 for o in selected for t in o.get('transforms',())),
                 mode_counts=dict(Counter(f"codec={o['codec']},prediction={o['pred']}" for o in selected)),
                 sha256=hashlib.sha256(data).hexdigest(),build_seconds=round(time.monotonic()-start,2))
    output.with_suffix('.stats.json').write_text(json.dumps(stats,indent=2)+'\n')
    print(json.dumps(stats,indent=2),flush=True)
    # Validate the actual serialized bytes and the completed RGBA, including private masks.
    from verify_emoji_pack import validate
    verification = validate(output, args.reference, args.textures)
    output.with_suffix('.verification.json').write_text(json.dumps(verification,indent=2)+'\n')

if __name__ == '__main__': main()
