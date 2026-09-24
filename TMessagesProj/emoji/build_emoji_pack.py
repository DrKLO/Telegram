#!/usr/bin/env python3
"""Reproduce the EPK3 v3 research build from the original PNG pack and textures.

All heavy optimization runs offline. Android needs only the supplied Java reader.
Use --base-pack to reuse the verified EPK3 v2 input and skip its build stage.
"""
import argparse,hashlib,importlib.util,json,os,pickle,shlex,shutil,subprocess,sys,time,zipfile
from pathlib import Path
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parent

def run(argv,cwd,env):
    print('+ '+' '.join(str(x)for x in argv),flush=True)
    subprocess.run([str(x)for x in argv],cwd=cwd,env=env,check=True)

def materialize(source,dest):
    if source.resolve()==dest.resolve():return
    dest.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(source,dest)

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--reference',required=True,help='Original PNG-based emoji.pack, not EPK3')
    ap.add_argument('--textures',required=True,help='Original 64x64 PNG ZIP')
    ap.add_argument('--output',default='emoji.pack')
    ap.add_argument('--base-pack',help='Optional verified EPK3 v2 pack (3,608,212 bytes)')
    ap.add_argument('--work-dir',help='Reusable build directory; contains large intermediate files')
    ap.add_argument('--workers',type=int,default=8)
    ap.add_argument('--all-experiments',action='store_true',help='Also reproduce the rejected mask/dictionary experiments')
    args=ap.parse_args()
    if args.workers<1:ap.error('--workers must be positive')
    reference=Path(args.reference).resolve();textures=Path(args.textures).resolve();output=Path(args.output).resolve()
    if reference==output:ap.error('Do not overwrite the original input')
    if reference.read_bytes()[:4]==b'EPK3':ap.error('--reference must be the original PNG-based pack')
    work=Path(args.work_dir).resolve()if args.work_dir else output.parent/'emoji_build'
    if work==ROOT:ap.error('Use a separate --work-dir')
    for d in ['upload','out/emoji-v2','work/v2','work/v3']:(work/d).mkdir(parents=True,exist_ok=True)
    env=os.environ.copy();env.update(OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',EMOJI_WORKERS=str(args.workers))
    started=time.monotonic()
    for name in ['optimizer_core.py','optimizer_visual.py','verify_emoji_pack.py','quality_seams.py']:
        materialize(ROOT/name,work/'out/emoji-v2'/name)
    materialize(reference,work/'upload/emoji.pack');materialize(textures,work/'upload/emoji_textures-5.zip')
    base=work/'out/emoji-v2/emoji.pack'
    if args.base_pack:materialize(Path(args.base_pack).resolve(),base)
    else:
        run([sys.executable,ROOT/'base_builder.py','--reference',reference,'--textures',textures,
             '--output',base,'--workers',args.workers,'--cache',work/'base-cache'],work,env)
    expected='258a9d78bb1a097377bca9355531403a83b71303222fa81b0bee111baec4186e'
    actual=hashlib.sha256(base.read_bytes()).hexdigest()
    if actual!=expected:
        raise ValueError('This measured research build expects the supplied v2 baseline. Check inputs and pinned Pillow/libwebp versions.')
    # Prepare exactly the same records consumed by every independent experiment.
    sys.path.insert(0,str(ROOT))
    import optimizer_core as core
    from verify_emoji_pack import Pack
    records,ne,lookup,_=core.read_reference(reference,textures)
    with open(work/'work/records.pkl','wb')as f:pickle.dump(records,f,protocol=5)
    with zipfile.ZipFile(textures)as z:
        originals=np.array([np.array(Image.open(z.open(f"{r['id']//4096}_{r['id']%4096}.png")).convert('RGBA'))for r in records[:ne]])
    np.savez_compressed(work/'work/originals.npz',rgba=originals)
    pack=Pack(base);selected=[]
    for i,e in enumerate(pack.entries):
        parents=tuple(p for p in e[4:6]if p!=65535)
        selected.append(dict(i=i,codec=e[7],pred=e[8],parents=parents,data=pack.blob(i),colors=e[6],channels=e[9],flags=e[10],paletteLength=pack.palette_length(e),cost=e[3],approx=bool(e[10]&128),transforms=tuple((e[10]>>(1+3*j))&7 for j in range(len(parents)))))
    with open(work/'work/v2/final_selected.pkl','wb')as f:pickle.dump(selected,f,protocol=5)
    current=np.array([pack.emoji(i)for i in range(ne)]);pack.close()
    np.savez_compressed(work/'work/v2/final_images.npz',rgba=current)
    for source in (ROOT/'experiments').iterdir():
        if source.is_file():materialize(source,work/'work/v3'/source.name)
    run(shlex.split(os.environ.get('CXX','c++'))+['-O3','-shared','-fPIC','work/v3/joint.cpp','-o','work/v3/joint.so'],work,env)
    stages=['joint_bench.py','joint_extra.py','select_joint.py','assemble_pack.py',
            'flexible_bench.py','flexible_extra.py','refit_halves.py','select_final.py']
    if args.all_experiments:stages+=['mask_bench.py','mask_select.py','dict_bench.py']
    for stage in stages:run([sys.executable,'work/v3/'+stage],work,env)
    output.parent.mkdir(parents=True,exist_ok=True)
    materialize(work/'work/v3/final.pack',output)
    run([sys.executable,ROOT/'verify_emoji_pack.py',output,'--reference',reference,'--textures',textures,
         '--compare-pack',base,'--json',output.with_suffix('.verification.json')],work,env)
    result=dict(pack_bytes=output.stat().st_size,v2_bytes=base.stat().st_size,
                saving_from_v2_bytes=base.stat().st_size-output.stat().st_size,
                sha256=hashlib.sha256(output.read_bytes()).hexdigest(),build_seconds=round(time.monotonic()-started,2))
    output.with_suffix('.stats.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2),flush=True)

if __name__=='__main__':main()
