"""Publish prepared assets only after repository push access is available.
Uses a token from the environment or Git Credential Manager, never logs/stores it.
"""
import argparse, json, os, subprocess, urllib.request, urllib.error
from pathlib import Path

def credential():
    token=os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
    if token:return token
    env=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='Never')
    result=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=env)
    fields=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
    if not fields.get('password'):raise SystemExit('Autentique uma conta com acesso de escrita no GitHub.')
    return fields['password']

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('folder');p.add_argument('--commit',required=True);a=p.parse_args()
    folder=Path(a.folder);manifest=json.loads((folder/'latest.json').read_text(encoding='utf-8'));tag='v'+manifest['versionName'];token=credential()
    headers={'Authorization':'Bearer '+token,'User-Agent':'LangerCamera-release','Accept':'application/vnd.github+json'}
    api='https://api.github.com/repos/Langeresilva/Langer-Camera'
    def request(url,data=None,method=None,content_type='application/json'):
        h=dict(headers);h['Content-Type']=content_type
        with urllib.request.urlopen(urllib.request.Request(url,data=data,headers=h,method=method),timeout=180) as r:return json.load(r)
    permissions=request(api).get('permissions',{})
    if not permissions.get('push'):raise SystemExit('A conta autenticada não tem acesso de escrita ao repositório. Nada foi publicado.')
    # Draft first: users should never observe a release with missing assets.
    body={'tag_name':tag,'target_commitish':a.commit,'name':'Langer Câmera '+manifest['versionName'],
          'body':manifest['notes']+'\n\nInstale o APK ARM64 ou use o pacote diferencial compatível pelo atualizador.','draft':True}
    release=request(api+'/releases',json.dumps(body).encode(),'POST')
    for asset in sorted(folder.iterdir()):
        if asset.is_file():
            from urllib.parse import quote
            request(release['upload_url'].split('{')[0]+'?name='+quote(asset.name),asset.read_bytes(),'POST','application/octet-stream')
            print('Enviado:',asset.name)
    release=request(api+'/releases/'+str(release['id']),json.dumps({'draft':False,'make_latest':'true'}).encode(),'PATCH')
    print('Publicado:',release['html_url'])
