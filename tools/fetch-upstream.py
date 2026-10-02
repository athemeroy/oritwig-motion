#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Optional original-source download and reproduction. Never overwrites vendor/."""
import argparse,concurrent.futures,hashlib,importlib.util,json,pathlib,urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True,help='External directory for the exact original source mirror');args=p.parse_args()
dest=pathlib.Path(args.output).resolve()
if dest==root or root in dest.parents:raise SystemExit('Original mirror must be outside the distribution source tree')
manifest=json.loads((root/'provenance/upstream-files.json').read_text())
spec=importlib.util.spec_from_file_location('notices',root/'provenance/apply-distribution-notices.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
def fetch(e):
 target=dest/e['path'];target.parent.mkdir(parents=True,exist_ok=True)
 if target.exists():data=target.read_bytes()
 else:
  with urllib.request.urlopen(e['url'],timeout=60) as response:data=response.read()
 blob=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
 if blob!=e['git_blob'] or hashlib.sha256(data).hexdigest()!=e['sha256']:raise ValueError('Original hash mismatch: '+e['path'])
 target.write_bytes(data)
 expected,_=module.transform(e['path'],data)
 if expected!=(root/e['path']).read_bytes():raise ValueError('Transformed source differs: '+e['path'])
 return e['path']
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
 result=list(pool.map(fetch,manifest['files']))
print('PASS',len(result),'original Git blobs and mechanically reproduced distribution files')
