"""Offline geometric candidates, palette fitting and perceptual quality checks."""
import io, zlib
import numpy as np
from scipy.ndimage import uniform_filter, distance_transform_edt
from scipy.spatial import cKDTree
from PIL import Image

def initialize(records, originals):
    global R, O, NE, N, M, RGBA, IDX, ALPHA
    R = records; O = originals; NE = len(O); N = len(R)
    M = {d['id']: NE + i for i, d in enumerate(R[NE:])}
    RGBA = np.array([d['rgba'] for d in R[:NE]])
    for i, d in enumerate(R[:NE]):
        if d['mask'] != 65535: RGBA[i, :, :, 3] = R[M[d['mask']]]['rgba'][:, :, 0]
    IDX = np.array([d['idx'] for d in R]); ALPHA = RGBA[:, :, :, 3]

def trans(a,t):
 if t&4:a=a.swapaxes(0,1)
 if t&2:a=a[::-1]
 if t&1:a=a[:,::-1]
 return a

def features(a):
 alpha=a[:,:,3:]/255.;rgb=a[:,:,:3]*alpha
 g=rgb.mean(axis=2);g=g/(g.std()+10)
 return np.concatenate([g.reshape(16,4,16,4).mean(axis=(1,3)).ravel(),alpha.reshape(16,4,16,4).mean(axis=(1,3)).ravel()*2]).astype(np.float32)

def make_candidates():
 global C
 F=np.array([features(a) for a in RGBA]);TREE=cKDTree(F)
 C=[[] for _ in range(NE)]
 for t in [0,1,2,3,4,5,6,7]:
  f=np.array([features(trans(a,t)) for a in RGBA]);_,js=TREE.query(f,k=9)
  for j,row in enumerate(js):
   for i in row:
    if i!=j:C[i].append((j,t))
 # Add direct near candidates; mirror query inversion above is exact involution only for 0..4,7.
 # Here the candidate is transformed j, queried against normal i, so all eight are correct.
 return C

def metrics(a,o):
 a=a.astype(np.float32)/255;o=o.astype(np.float32)/255
 ms=ss=p99=0.
 for bg in [0,1]:
  x=a[:,:,:3]*a[:,:,3:]+bg*(1-a[:,:,3:]);y=o[:,:,:3]*o[:,:,3:]+bg*(1-o[:,:,3:]);err=(x-y)**2
  ms+=float(np.mean(err));p99+=float(np.quantile(err,.99))
  mx=uniform_filter(x,size=(7,7,1));my=uniform_filter(y,size=(7,7,1));vx=uniform_filter(x*x,size=(7,7,1))-mx*mx;vy=uniform_filter(y*y,size=(7,7,1))-my*my;cv=uniform_filter(x*y,size=(7,7,1))-mx*my
  ss+=float(np.mean(((2*mx*my+.0001)*(2*cv+.0009))/((mx*mx+my*my+.0001)*(vx+vy+.0009))))
 return ms/2,ss/2,p99/2

def fit(i,j,t):
 ix=trans(IDX[j],t);n=len(R[j]['pal']);a=ALPHA[i].astype(np.float32)/255;o=O[i].astype(np.float32);ao=o[:,:,3]/255
 weight=a*a;wanted=np.zeros((64,64,3),np.float32);valid=a>0
 wanted[valid]=(o[valid,:3]*ao[valid,None]+127.5*(a[valid,None]-ao[valid,None]))/a[valid,None]
 den=np.bincount(ix.ravel(),weight.ravel(),minlength=n)
 pal=np.array([np.bincount(ix.ravel(),(wanted[:,:,c]*weight).ravel(),minlength=n)/np.maximum(den,1e-6) for c in range(3)]).T
 pal=np.clip(np.round(pal),0,255).astype(np.uint8)
 output=np.empty((64,64,4),np.uint8);output[:,:,:3]=pal[ix];output[:,:,3]=ALPHA[i]
 return output,pal

def template_job(i):
 if R[i]['mask']==65535:return [],None
 ref=metrics(RGBA[i],O[i]);options=[]
 for j,t in C[i]:
  if len(R[j]['pal'])>150:continue
  a,pal=fit(i,j,t);met=metrics(a,O[i]);good=(met[0]<=ref[0] and met[1]>=ref[1] and met[2]<=ref[2])
  if good:options.append((len(pal)*3,j,t,pal,met))
 options.sort(key=lambda x:x[0]);return options[:8],ref

def half_features(a):
 alpha=a[:,:,3:]/255.;g=(a[:,:,:3]*alpha).mean(axis=2);g/=g.std()+10
 return np.concatenate([g.reshape(16,4,8,4).mean(axis=(1,3)).ravel(),alpha.reshape(16,4,8,4).mean(axis=(1,3)).ravel()*2]).astype(np.float32)
def make_half_candidates():
 global HC
 HC=[[[] for _ in range(NE)] for _ in range(2)]
 for side in [0,1]:
  target=np.array([half_features(a[:,side*32:(side+1)*32]) for a in RGBA]);tree=cKDTree(target)
  for t in [0,1]:
   f=np.array([half_features(trans(a,t)[:,side*32:(side+1)*32]) for a in RGBA]);_,js=tree.query(f,k=12)
   for j,row in enumerate(js):
    for i in row:
     if i!=j:HC[side][i].append((j,t))
 return HC

def fit_half(i,j,t,side):
 x=side*32;ix=trans(IDX[j],t)[:,x:x+32];n=len(R[j]['pal']);a=ALPHA[i,:,x:x+32].astype(np.float32)/255;o=O[i,:,x:x+32].astype(np.float32);ao=o[:,:,3]/255
 wt=a*a;valid=a>0;wanted=np.zeros((64,32,3),np.float32)
 wanted[valid]=(o[valid,:3]*ao[valid,None]+127.5*(a[valid,None]-ao[valid,None]))/a[valid,None]
 den=np.bincount(ix.ravel(),wt.ravel(),minlength=n)
 pal=np.array([np.bincount(ix.ravel(),(wanted[:,:,c]*wt).ravel(),minlength=n)/np.maximum(den,1e-6) for c in range(3)]).T
 pal=np.clip(np.round(pal),0,255).astype(np.uint8)
 out=np.empty((64,32,4),np.uint8);out[:,:,:3]=pal[ix];out[:,:,3]=ALPHA[i,:,x:x+32]
 return out,pal

def half_template_job(i):
 if R[i]['mask']==65535:return []
 halves=[]
 for side in [0,1]:
  x=side*32;original=O[i,:,x:x+32];ref=metrics(RGBA[i,:,x:x+32],original);opts=[]
  for j,t in HC[side][i]:
   if len(R[j]['pal'])>150:continue
   out,pal=fit_half(i,j,t,side);met=metrics(out,original)
   if met[0]<=ref[0] and met[1]>=ref[1] and met[2]<=ref[2]:opts.append((len(np.unique(pal,axis=0)),j,t,pal,met))
  opts.sort(key=lambda x:x[0]);halves.append(opts[:4])
 if not halves[0] or not halves[1]:return []
 options=[]
 for l in halves[0]:
  for r in halves[1]:
   pal,inv=np.unique(np.concatenate([l[3],r[3]]),axis=0,return_inverse=True)
   if len(pal)>256:continue
   raw=inv.astype(np.uint8).tobytes()+bytes(4096);zz=zlib.compress(raw,9)
   options.append((len(pal)*3+len(zz),l[1],r[1],l[2],r[2],pal,zz,l[4],r[4]))
 return sorted(options,key=lambda x:x[0])[:6]

def webp(a):
 b=io.BytesIO();Image.fromarray(a).save(b,format='WEBP',lossless=True,quality=100,method=6,exact=True);return b.getvalue()

def mirror_job(i):
 a=IDX[i];visible=ALPHA[i]>0 if i<NE and R[i]['mask']!=65535 else np.ones((64,64),bool)
 cand=set(C[i]) if i<NE else set()
 cand.update((x[1],t) for x in D[i][1] for t in range(8))
 options=[]
 for j,t in cand:
  base=trans(IDX[j],t);nb=len(R[j]['pal'])
  cnt=np.bincount((base[visible].astype(np.int32)*256+a[visible]).ravel(),minlength=65536).reshape(256,256)
  lut=cnt.argmax(axis=1).astype(np.uint8)[:nb];p=lut[base]
  for mode in [1,2]:
   delta=(a^p) if mode==1 else (a-p);delta[~visible]=0
   zz=zlib.compress(lut.tobytes()+delta.tobytes(),9)
   options.append((len(zz),j,t,mode,zz,lut.tobytes(),delta))
 options.sort(key=lambda x:x[0]);out=[]
 for _,j,t,mode,zz,lut,delta in options[:6]:
  wb=webp(delta)
  out.append(dict(parent=j,transform=t,pred=mode,zlib=zz,webp=wb,lut=lut))
 return out

def native_webp_job(i):
 d=R[i];src=O[i].copy();ref=d['rgba'].copy()
 if d['mask']!=65535:ref[:,:,3]=R[M[d['mask']]]['idx']
 # Keep alpha exactly as current reference. Recover source RGB gradients before WebP.
 src[:,:,3]=ref[:,:,3]
 m=src[:,:,3]<16
 if np.any(~m):
  iy,ix=distance_transform_edt(m,return_distances=False,return_indices=True);src[m,:3]=src[iy[m],ix[m],:3]
 encode=src.copy()
 if d['mask']!=65535:encode[:,:,3]=255
 rr=metrics(ref,O[i]);out=[];chosen=None
 for q in [10,20,30,40,50,60,70,80,90,95,100]:
  b=io.BytesIO();Image.fromarray(encode).save(b,format='WEBP',quality=q,method=6,alpha_quality=100);b=b.getvalue();a=np.array(Image.open(io.BytesIO(b)).convert('RGBA'));a[:,:,3]=ref[:,:,3]
  sc=metrics(a,O[i]);good=sc[0]<=rr[0] and sc[1]>=rr[1] and sc[2]<=rr[2]
  out.append((q,len(b),sc,good))
  if good:
   chosen=(q,b,sc);break
 return chosen,rr,out
def error(a,o):
 a=a.astype(np.float32)/255;o=o.astype(np.float32)/255
 da=a[:,:,3:]-o[:,:,3:];rgb=a[:,:,:3]*a[:,:,3:]-o[:,:,:3]*o[:,:,3:]
 return ((rgb*rgb+(rgb-da)*(rgb-da))/2).mean(axis=2)

def structure(a,o):
 a=a.astype(np.float32)/255;o=o.astype(np.float32)/255;s=0
 for bg in [0,1]:
  x=a[:,:,:3]*a[:,:,3:]+bg*(1-a[:,:,3:]);y=o[:,:,:3]*o[:,:,3:]+bg*(1-o[:,:,3:])
  mx=uniform_filter(x,size=(5,5,1));my=uniform_filter(y,size=(5,5,1));vx=uniform_filter(x*x,size=(5,5,1))-mx*mx;vy=uniform_filter(y*y,size=(5,5,1))-my*my;cv=uniform_filter(x*y,size=(5,5,1))-mx*my
  s+=(((2*mx*my+.0001)*(2*cv+.0009))/((mx*mx+my*my+.0001)*(vx+vy+.0009))).mean(axis=2)
 return s/2



def passes_global(image, original, reference_metrics):
    m = metrics(image, original)
    return m[0] <= reference_metrics[0] and m[1] >= reference_metrics[1] and m[2] <= reference_metrics[2]


def passes_local(image, original, reference_error, reference_structure):
    e = uniform_filter(error(image, original), size=5)
    s = structure(image, original)
    return bool(np.all(e <= np.maximum(reference_error * 3, (8 / 255) ** 2))
                and np.all(s >= reference_structure - 0.1))
