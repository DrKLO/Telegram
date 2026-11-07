#!/usr/bin/env python3
import sys, struct, zlib, os
def load(p):
    data=open(p,'rb').read(); pos=8; idat=b''
    while pos<len(data):
        ln=struct.unpack('>I',data[pos:pos+4])[0]; t=data[pos+4:pos+8]; c=data[pos+8:pos+8+ln]; pos+=12+ln
        if t==b'IHDR': w,h,bd,ct=struct.unpack('>IIBB',c[:10])
        elif t==b'IDAT': idat+=c
    raw=zlib.decompress(idat); bpp=4 if ct==6 else 3; stride=w*bpp; out=bytearray(); prev=bytearray(stride); i=0
    for y in range(h):
        f=raw[i]; i+=1; line=bytearray(raw[i:i+stride]); i+=stride
        if f==2:
            line=bytearray((x+y_)&255 for x,y_ in zip(line,prev))
        elif f:
            for x in range(stride):
                a=line[x-bpp] if x>=bpp else 0; b=prev[x]; c=prev[x-bpp] if x>=bpp else 0
                if f==1: line[x]=(line[x]+a)&255
                elif f==3: line[x]=(line[x]+((a+b)>>1))&255
                elif f==4:
                    p_=a+b-c; pa,pb,pc=abs(p_-a),abs(p_-b),abs(p_-c)
                    line[x]=(line[x]+(a if pa<=pb and pa<=pc else b if pb<=pc else c))&255
        out+=line; prev=line
    return w,h,bpp,out
for f in sys.argv[1:]:
    w,h,bpp,a=load(f); w2,h2,_,b=load(f.replace('-got','-want'))
    if (w,h)!=(w2,h2): print(os.path.basename(f),'SIZE',w,h,w2,h2); continue
    n=0;mx=0;x0=y0=10**9;x1=y1=-1;hist={}
    for i in range(0,len(a),bpp):
        d=max(abs(a[i+k]-b[i+k]) for k in range(bpp))
        if d:
            n+=1; mx=max(mx,d); hist[min(d,64)]=hist.get(min(d,64),0)+1; p=i//bpp; x,y=p%w,p//w; x0=min(x0,x);x1=max(x1,x);y0=min(y0,y);y1=max(y1,y)
    big=sum(v for k,v in hist.items() if k>8)
    print(f'{os.path.basename(f):38s} {w}x{h} diffpx={n} maxdiff={mx} >8:{big} bbox=({x0},{y0})-({x1},{y1})')
