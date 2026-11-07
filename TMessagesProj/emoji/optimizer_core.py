#!/usr/bin/env python3
"""Internal lossless candidate search used by build_emoji_pack.py.

Build dependencies: Python 3.11+, numpy, scipy, Pillow with WebP support.
Runtime dependencies on Android: BitmapFactory and java.util.zip.Inflater only.
This tool retains the existing quantization and dithering exactly. It does not
run a new lossy quantizer. --textures optionally validates the original ZIP IDs.
"""
import argparse, hashlib, heapq, io, json, multiprocessing, struct, time, zipfile, zlib
from pathlib import Path
from collections import Counter
from concurrent.futures import ProcessPoolExecutor
import numpy as np
from scipy.spatial import cKDTree
from PIL import Image, features as pil_features
NONE = 65535

def png_read(b):
 im=Image.open(io.BytesIO(b));a=np.array(im.convert('RGBA'))
 if im.mode=='P':
  idx=np.array(im);n=int(idx.max())+1;pal=np.zeros((n,4),np.uint8);pal[:,:3]=np.array(im.getpalette()[:n*3]).reshape(n,3);pal[:,3]=255
  tr=im.info.get('transparency',b'')
  if isinstance(tr,int):pal[tr,3]=0
  else:pal[:len(tr),3]=np.frombuffer(tr,dtype=np.uint8)
 elif im.mode=='L':
  idx=np.array(im);pal=np.arange(256,dtype=np.uint8)[:,None].repeat(4,axis=1);pal[:,3]=255
 else:
  pal,inv=np.unique(a.reshape(-1,4),axis=0,return_inverse=True);assert len(pal)<=256;idx=inv.reshape(64,64).astype(np.uint8)
 assert np.array_equal(pal[idx],a)
 return {'rgba':a,'idx':idx,'pal':pal,'png':b,'mode':im.mode}


def read_reference(path, textures=None):
    data = Path(path).read_bytes()
    def take(pos, count):
        if pos < 0 or count < 0 or pos + count > len(data):
            raise ValueError("Truncated reference pack")
        return data[pos:pos + count]
    n = struct.unpack('<I', take(0, 4))[0]
    if n % 12: raise ValueError("Invalid reference emoji table")
    n //= 12
    em = [struct.unpack('<HHII', take(4 + i * 12, 12)) for i in range(n)]
    pos = 4 + n * 12
    m = struct.unpack('<I', take(pos, 4))[0]
    if m % 10: raise ValueError("Invalid reference mask table")
    m //= 10
    masks = [struct.unpack('<HII', take(pos + 4 + i * 10, 10)) for i in range(m)]
    records = []
    for eid, mid, off, length in sorted(em):
        d = png_read(take(off, length))
        d.update(id=eid, mask=mid, kind='emoji')
        records.append(d)
    for mid, off, length in sorted(masks):
        d = png_read(take(off, length))
        d.update(id=mid, mask=NONE, kind='mask')
        records.append(d)
    if not records or len(records) >= NONE:
        raise ValueError("Invalid record count")
    for d in records:
        if d['rgba'].shape != (64, 64, 4):
            raise ValueError("This format requires 64x64 textures")
    if len({d['id'] for d in records[:n]}) != n or len({d['id'] for d in records[n:]}) != m:
        raise ValueError("Duplicate IDs")
    lookup = {d['id']: n + i for i, d in enumerate(records[n:])}
    for d in records[:n]:
        if d['mask'] != NONE and d['mask'] not in lookup:
            raise ValueError("Unknown mask ID")
    if textures:
        with zipfile.ZipFile(textures) as archive:
            for d in records[:n]:
                name = f"{d['id'] // 4096}_{d['id'] % 4096}.png"
                with Image.open(archive.open(name)) as image:
                    if image.size != (64, 64): raise ValueError(name)
            pngs = [x for x in archive.namelist() if x.lower().endswith('.png')]
            if len(pngs) != n: raise ValueError("ZIP/reference emoji count mismatch")
    return records, n, lookup, len(data)


def baseline(d):
    b = io.BytesIO()
    Image.fromarray(d['rgba']).save(b, format='WEBP', lossless=True,
                                  quality=100, method=6, exact=True)
    return {'webp': b.getvalue()}
def candidates():
 # Coarse luma/alpha features and nearest exact silhouettes, plus local variant sequences.
 fs=[]
 for d in R:
  a=d['rgba'].astype(np.float32);al=a[:,:,3:]/255
  if d['kind']=='emoji' and d['mask']!=65535:al=R[MASK_LOOKUP[d['mask']]]['rgba'][:,:,:1]/255
  c=a[:,:,:3]*al
  gray=c.mean(axis=2);gray=gray/(gray.std()+10)
  feat=np.concatenate([gray.reshape(16,4,16,4).mean(axis=(1,3)).ravel(),al.reshape(16,4,16,4).mean(axis=(1,3)).ravel()*2])
  fs.append(feat)
 tree=cKDTree(np.array(fs));near=tree.query(fs,k=25)[1]
 out=[]
 for i,d in enumerate(R):
  ids=set(near[i]);ids.update(range(max(0,i-6),min(N,i+7)));ids.discard(i)
  out.append(sorted(j for j in ids if R[j]['kind']==d['kind']))
 return out

def encode_delta(i):
 a=IDX[i];d=R[i];ch=4 if np.any(d['pal'][:,3]!=255) else 3;pal=len(d['pal'])*ch if d['kind']=='emoji' else 0
 raw=a.tobytes();best={'cost':len(zlib.compress(raw,9))+pal,'parent':-1,'mode':0,'data':zlib.compress(raw,9),'lut':b''}
 options=[]
 for j in C[i]:
  base=IDX[j];nb=len(R[j]['pal'])
  cnt=np.bincount((base.astype(np.int32)*256+a).ravel(),minlength=65536).reshape(256,256)
  lut=cnt.argmax(axis=1).astype(np.uint8)[:nb]
  pred=lut[base]
  # One data stream: LUT + mapped-palette residual. Original indices always restored exactly.
  for mode,delta in [(1,np.bitwise_xor(a,pred)),(2,a-pred)]:
   zz=zlib.compress(lut.tobytes()+delta.tobytes(),9);cost=len(zz)+pal+2
   options.append((cost,j,mode,zz,lut.tobytes()))
 options.sort(key=lambda x:x[0]);best3=options[:3]
 if best3 and best3[0][0]<best['cost']:
  co,j,mo,zz,lut=best3[0];best={'cost':co,'parent':j,'mode':mo,'data':zz,'lut':lut}
 return best,best3
def webp(a):
 b=io.BytesIO();Image.fromarray(a).save(b,format='WEBP',lossless=True,quality=100,method=6,exact=True);return b.getvalue()
def encode_delta_webp(i):
 d=R[i];res=[];pal=len(d['pal'])*(4 if np.any(d['pal'][:,3]!=255) else 3) if d['kind']=='emoji' else 0
 direct=webp(d['idx'])
 for co,j,mo,zz,lut in D[i][1]:
  data=zlib.decompress(zz);a=np.frombuffer(data[len(lut):],np.uint8).reshape(64,64);b=webp(a)
  res.append((len(b)+len(lut)+pal+2,j,mo,b,lut))
 return direct,sorted(res,key=lambda x:x[0])

def half_candidates():
    features=[]
    for side in range(2):
     f=[]
     for d in R:
      a=d['rgba'].astype(np.float32)[:,side*32:(side+1)*32];al=a[:,:,3:]/255
      if d['kind']=='emoji' and d['mask']!=65535:al=R[MASK_LOOKUP[d['mask']]]['idx'][:,side*32:(side+1)*32,None]/255
      g=(a[:,:,:3]*al).mean(axis=2);g=g/(g.std()+10)
      f.append(np.concatenate([g.reshape(16,4,8,4).mean(axis=(1,3)).ravel(),al.reshape(16,4,8,4).mean(axis=(1,3)).ravel()*2]))
     features.append(cKDTree(f).query(f,k=13)[1])
    return features

def encode_half(i):
 if R[i]['kind']=='mask':return []
 opts=[]
 for side in range(2):
  candidates=set(int(j) for j in features[side][i] if j!=i and j<NE)
  candidates.update(x[1] for x in D[i][1] if x[1]<NE)
  a=I[i,:,side*32:(side+1)*32];out=[]
  for j in candidates:
   base=I[j,:,side*32:(side+1)*32];cnt=np.bincount((base.astype(np.int32)*256+a).ravel(),minlength=65536).reshape(256,256);lut=cnt.argmax(axis=1).astype(np.uint8)[:len(R[j]['pal'])];p=lut[base];delta=a-p
   zz=zlib.compress(lut.tobytes()+delta.tobytes(),9)
   out.append((len(zz),j,lut,delta))
  out.sort(key=lambda x:x[0]);opts.append(out[:3])
 candidates=[]
 for a in opts[0]:
  for b in opts[1]:
   lut=a[2].tobytes()+b[2].tobytes();delta=np.concatenate([a[3],b[3]],axis=1)
   zz=zlib.compress(lut+delta.tobytes(),9);wb=io.BytesIO();Image.fromarray(delta).save(wb,format='WEBP',lossless=True,quality=100,method=6,exact=True);wb=wb.getvalue()
   codec,data=(0,zz) if len(zz)<=len(lut)+len(wb) else (1,lut+wb)
   candidates.append((len(data)+4,a[1],b[1],codec,data))
 return sorted(candidates)[:4]
def palette(d):
 if d['kind']=='mask':
  if len(d['pal'])==256 and np.array_equal(d['pal'][:,0],np.arange(256,dtype=np.uint8)):
   return 0,b'' # implicit grayscale 0..255
  return 1,d['pal'][:,0].tobytes()
 c=4 if np.any(d['pal'][:,3]!=255) else 3
 return c,d['pal'][:,:c].tobytes()

def strip_webp(b):
 assert b[:4]==b'RIFF' and b[8:16]==b'WEBPVP8L'
 n=struct.unpack_from('<I',b,16)[0]
 assert len(b)==20+n+(n&1)
 return b[20:20+n]

def wrap_webp(b):
 return b'RIFF'+struct.pack('<I',12+len(b)+(len(b)&1))+b'WEBPVP8L'+struct.pack('<I',len(b))+b+(b'\0' if len(b)&1 else b'')

def pack7(b):
 acc=0;bits=0;out=bytearray()
 for v in b:
  acc|=(v>>1)<<bits;bits+=7
  while bits>=8:out.append(acc&255);acc>>=8;bits-=8
 if bits:out.append(acc)
 return bytes(out)

def option(i,codec,pred,parents,data):
 ch,pal=palette(R[i]);pcount=len(R[i]['pal']) if ch else 256
 flags=0
 if pal and all(v==((v>>1)*2+(v>>7)) for v in pal):pal=pack7(pal);flags=1
 if codec>=2:pal=b'';ch=0;pcount=0;flags=0
 if codec==1:
  ln=sum(len(R[p]['pal']) for p in parents);data=data[:ln]+strip_webp(data[ln:])
 elif codec==2:data=strip_webp(data)
 return dict(i=i,codec=codec,pred=pred,parents=parents,data=pal+data,colors=pcount,channels=ch,flags=flags,paletteLength=len(pal),cost=len(pal)+len(data))

def prepare():
 roots=[];ind=[];edges=[]
 for i in range(N):
  r=R[i];raw=zlib.compress(r['idx'].tobytes(),9)[2:-4]
  root=min([option(i,0,0,(),raw),option(i,1,0,(),W[i][0])],key=lambda x:x['cost']);roots.append(root)
  full=[option(i,2,0,(),B[i]['webp']),option(i,3,0,(),r['png'])]
  ind.append(min(full+[root],key=lambda x:x['cost']))
  oo=[]
  for _,j,mode,zz,lut in D[i][1]:oo.append(option(i,0,mode,(j,),zz[2:-4]))
  for _,j,mode,wb,lut in W[i][1]:oo.append(option(i,1,mode,(j,),lut+wb))
  edges.append(sorted(oo,key=lambda x:x['cost']))
 return roots,ind,edges

def select(roots,ind,edges,allow_half=True):
 if allow_half:
  for i,oo in enumerate(H):
   for _,a,b,codec,data in oo:
    if codec==0:data=data[2:-4]
    edges[i].append(option(i,codec,3,(a,b),data))
 incoming=[[] for _ in range(N)]
 for i,oo in enumerate(edges):
  for o in oo:
   for j in set(o['parents']):incoming[j].append(o)
 costs=[o['cost'] for o in ind];choice=ind.copy();opened=set();heap=[]
 def gain(j):
  if j in opened:return -1e9
  changes={}
  for o in incoming[j]:
   i=o['i']
   if i in opened or i==j:continue
   if all(p==j or p in opened for p in o['parents']):changes[i]=min(changes.get(i,costs[i]),o['cost'])
  return sum(costs[i]-c for i,c in changes.items())-(roots[j]['cost']-costs[j])
 for j in range(N):heapq.heappush(heap,(-gain(j),j))
 while heap:
  ng,j=heapq.heappop(heap)
  if j in opened:continue
  g=gain(j)
  if heap and g < -heap[0][0]-.01:
   heapq.heappush(heap,(-g,j));continue
  if g<=0:break
  opened.add(j);costs[j]=roots[j]['cost'];choice[j]=roots[j]
  for o in incoming[j]:
   i=o['i']
   if i not in opened and all(p in opened for p in o['parents']) and o['cost']<costs[i]:costs[i]=o['cost'];choice[i]=o
  # Half options become available as soon as one of the two roots is open.
  for o in incoming[j]:
   for p in o['parents']:
    if p not in opened:heapq.heappush(heap,(-gain(p),p))
 # Prune facilities no longer used by anyone.
 used={p for o in choice for p in o['parents']}
 for j in opened-used:choice[j]=ind[j]
 return choice

def pack(choice,path):
 hsize=32;esize=20;off=hsize+N*esize;entries=[];payload=[]
 for i,o in enumerate(choice):
  r=R[i];p=o['parents'];data=o['data'];assert len(data)<65536
  entries.append(struct.pack('<HHIHHHHBBBB',r['id'],r['mask'],off,len(data),p[0] if p else NONE,p[1] if len(p)>1 else NONE,o['colors'],o['codec'],o['pred'],o['channels'],o['flags']));payload.append(data);off+=len(data)
 header=struct.pack('<4sHHIIIIII',b'EPK3',1,esize,64,64,N,NE,off,0)
 b=header+b''.join(entries)+b''.join(payload);assert len(b)==off;path.write_bytes(b)
 return b

def verify(choice):
 roots={}
 def idx(i):
  o=choice[i];data=o['data'];n=o['paletteLength'];data=data[n:];ps=o['parents'];maps=[idx(p) for p in ps]
  ls=[choice[p]['colors'] for p in ps];ln=sum(ls)
  if o['codec']==0:
   raw=zlib.decompress(data,-15);lut=raw[:ln];a=np.frombuffer(raw[ln:],np.uint8).copy().reshape(64,64)
  elif o['codec']==1:
   lut=data[:ln];a=np.array(Image.open(io.BytesIO(wrap_webp(data[ln:]))).convert('L'))
  else:raise ValueError('not indexed')
  if ps:
   if o['pred']==3:
    l=np.frombuffer(lut[:ls[0]],np.uint8)[maps[0]][:,:32];r=np.frombuffer(lut[ls[0]:],np.uint8)[maps[1]][:,32:];p=np.concatenate([l,r],axis=1)
   else:p=np.frombuffer(lut,np.uint8)[maps[0]]
   a=(a^p) if o['pred']==1 else (a+p).astype(np.uint8)
  assert np.array_equal(a,R[i]['idx']),(i,'indices')
  return a
 for i,o in enumerate(choice):
  if o['codec']>=2:a=np.array(Image.open(io.BytesIO(wrap_webp(o['data']) if o['codec']==2 else o['data'])).convert('RGBA'))
  else:
   ix=idx(i);a=R[i]['pal'][ix]
  assert np.array_equal(a,R[i]['rgba']),(i,'rgba')
 print('verified all',len(choice),'images and masks exactly',flush=True)


def map_jobs(fn, jobs, workers, label):
    start = time.monotonic()
    out = []
    # Workers inherit read-only arrays. Explicit fork avoids Python 3.14's
    # changed default; this build tool targets Linux/macOS, not Android.
    ctx = multiprocessing.get_context('fork')
    with ProcessPoolExecutor(max_workers=workers, mp_context=ctx) as pool:
        for i, result in enumerate(pool.map(fn, jobs, chunksize=8)):
            out.append(result)
            if (i + 1) % 500 == 0:
                print(f'{label}: {i + 1}; {time.monotonic() - start:.1f}s', flush=True)
    return out

