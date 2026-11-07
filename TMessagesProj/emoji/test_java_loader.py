#!/usr/bin/env python3
"""Host JVM logic test. BitmapFactory is stubbed with exact libwebp fixtures.

This is deliberately not an Android performance/premultiplication test.
Requires the build Python dependencies and a JDK with com.sun.tools.javac.Main.
"""
import argparse,hashlib,io,shutil,struct,subprocess,tempfile
from pathlib import Path
import numpy as np
from PIL import Image
from verify_emoji_pack import Pack,wrap_webp
ROOT=Path(__file__).resolve().parent

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('pack');ap.add_argument('--work-dir')
    args=ap.parse_args();work=Path(args.work_dir).resolve()if args.work_dir else Path(tempfile.mkdtemp(prefix='emoji-java-test-'))
    work.mkdir(parents=True,exist_ok=True);fixtures=work/'fixtures';fixtures.mkdir(exist_ok=True)
    p=Pack(args.pack)
    for i,e in enumerate(p.entries):
        if e[7]in (0,4):continue
        b=p.blob(i)
        if e[7]==1:
            n=sum(p.entries[j][6]for j in e[4:6]if j!=65535)
            b=wrap_webp(b[p.palette_length(e)+(1 if e[8]in(5,6)else 0)+n:])
        elif e[7]==2:b=wrap_webp(b)
        rgba=np.array(Image.open(io.BytesIO(b)).convert('RGBA')).tobytes()
        (fixtures/hashlib.sha256(b).hexdigest()).write_bytes(rgba)
    expected=work/'expected.bin'
    with expected.open('wb')as f:
        f.write(struct.pack('>II',p.ne,p.ne))
        for i in range(p.ne):
            key,mask=p.entries[i][:2];a=p.emoji(i).astype(np.uint32)
            argb=(a[:,:,3]<<24)|(a[:,:,0]<<16)|(a[:,:,1]<<8)|a[:,:,2]
            f.write(struct.pack('>HH',key,mask));f.write(argb.astype('>u4').tobytes())
    asset=work/'asset.bin';asset.write_bytes(b'X'*137+bytes(p.data)+b'TRAILER');p.close()
    sources=sorted((ROOT/'tests/src').rglob('*.java'))+[ROOT/'EmojiPack.java']
    classes=work/'classes';classes.mkdir(exist_ok=True)
    subprocess.run(['java','com.sun.tools.javac.Main','-source','8','-target','8','-d',str(classes)]+[str(s)for s in sources],check=True)
    subprocess.run(['java','-Dfixture.dir='+str(fixtures),'-Dfixture.asset='+str(asset),'-cp',str(classes),'Test',str(expected)],check=True)
    print('Fixtures:',work)
if __name__=='__main__':main()
