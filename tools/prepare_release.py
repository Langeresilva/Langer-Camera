"""Prepare GitHub Release assets and channel manifest locally; never uploads credentials."""
import argparse, hashlib, json, subprocess, sys
from pathlib import Path

def digest(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()

if __name__ == '__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--apk',required=True);p.add_argument('--version',required=True);p.add_argument('--code',required=True,type=int)
    p.add_argument('--base');p.add_argument('--base-code',type=int);p.add_argument('--base-version')
    p.add_argument('--notes',default='Melhorias do Langer Câmera.')
    a=p.parse_args();root=Path(__file__).resolve().parent.parent
    apk=Path(a.apk).resolve();folder=root/'entrega'/('release-v'+a.version);folder.mkdir(parents=True,exist_ok=True)
    import shutil
    destination=folder/apk.name;shutil.copy2(apk,destination)
    base_url='https://github.com/Langeresilva/Langer-Camera/releases/download/v'+a.version+'/'
    manifest={'packageName':'br.com.luma.camera','versionCode':a.code,'versionName':a.version,
              'notes':a.notes,'sha256':digest(apk),'apkUrl':base_url+apk.name}
    if a.base:
        if not a.base_code or not a.base_version:p.error('--base requires --base-code and --base-version')
        patch=folder/('LangerCamera-'+a.base_version+'-para-'+a.version+'.lgrdelta')
        subprocess.run([sys.executable,str(root/'tools'/'make_delta.py'),a.base,str(apk),str(patch)],check=True)
        manifest['delta']={'fromVersionCode':a.base_code,'baseSha256':digest(a.base),'url':base_url+patch.name}
    (folder/'latest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (folder/'SHA256SUMS.txt').write_text(''.join(digest(f)+'  '+f.name+'\n' for f in folder.iterdir() if f.is_file() and f.name!='SHA256SUMS.txt'),encoding='utf-8')
    print('Release preparada em:',folder)
