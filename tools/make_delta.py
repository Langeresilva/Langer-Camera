"""Create a ZIP-aware differential patch. Preserves exact APK bytes and signature."""
import argparse, gzip, hashlib, struct, zipfile, json
from pathlib import Path

def sha(data): return hashlib.sha256(data).digest()
def spans(path, data):
    with zipfile.ZipFile(path) as z:
        entries = sorted(z.infolist(), key=lambda e: e.header_offset)
        return [(e.filename, e.header_offset, entries[i+1].header_offset if i+1<len(entries) else z.start_dir)
                for i,e in enumerate(entries)]

def make(base_path, target_path, destination):
    base, target = Path(base_path).read_bytes(), Path(target_path).read_bytes()
    old = {name:(start,end) for name,start,end in spans(base_path,base)}
    operations=[]; position=0
    for name,start,end in spans(target_path,target):
        if start>position: operations.append((1,target[position:start]))
        previous=old.get(name)
        if previous and base[previous[0]:previous[1]]==target[start:end]:
            operations.append((0,(previous[0],end-start)))
        else: operations.append((1,target[start:end]))
        position=end
    if position<len(target): operations.append((1,target[position:]))
    with gzip.open(destination,'wb',compresslevel=9) as out:
        out.write(b'LGRD1'+sha(base)+sha(target)+struct.pack('>q',len(target)))
        for op,value in operations:
            out.write(bytes([op]))
            if op==0: out.write(struct.pack('>qi',*value))
            else: out.write(struct.pack('>i',len(value)));out.write(value)
        out.write(b'\xff')
    print(json.dumps({'baseSha256':sha(base).hex(),'sha256':sha(target).hex(),
                      'apkBytes':len(target),'patchBytes':Path(destination).stat().st_size},indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('base');parser.add_argument('target');parser.add_argument('output')
    args=parser.parse_args();make(args.base,args.target,args.output)
