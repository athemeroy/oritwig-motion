#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check native alignment, DT_NEEDED, permissions and complete binary notices."""
from pathlib import Path
import os,subprocess,zipfile,tempfile,json,hashlib
r=Path(__file__).resolve().parents[1];sdk=Path(os.environ['ANDROID_HOME']);apk=r/'app/build/outputs/apk/debug/app-debug.apk'
readelf=sdk/'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
aapt=sdk/'build-tools/35.0.0/aapt2';zipalign=sdk/'build-tools/35.0.0/zipalign'
permissions=subprocess.check_output([str(aapt),'dump','permissions',str(apk)],text=True)
assert 'uses-permission' not in permissions,permissions
result={'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'permissions':permissions,'native':[],'notices':[]}
with tempfile.TemporaryDirectory() as temp,zipfile.ZipFile(apk) as z:
 for name in z.namelist():
  if name.startswith('lib/') and name.endswith('.so'):
   path=Path(temp)/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(z.read(name));headers=subprocess.check_output([str(readelf),'-lW',str(path)],text=True);deps=subprocess.check_output([str(readelf),'-dW',str(path)],text=True)
   loads=[line.split() for line in headers.splitlines() if line.strip().startswith('LOAD')]
   required=4096 if name=='lib/x86/libc++_shared.so' else 16384
   assert loads and all(int(line[-1],16)>=required for line in loads),(name,headers)
   sections=subprocess.check_output([str(readelf),'-SW',str(path)],text=True);assert '.debug_info' not in sections,name
   if name.endswith('liboritwig_motion_jni.so'):assert 'Shared library: [librlottie.so]' in deps,(name,deps)
   assert 'tmessages' not in deps and 'tgnet' not in deps
   result['native'].append({'path':name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'load_alignments':[line[-1] for line in loads],'dynamic':deps})
 for path in sorted((r/'provenance/licenses').iterdir()):
  if path.is_file():assert z.read('assets/licenses/'+path.name)==path.read_bytes();result['notices'].append(path.name)
 assert z.read('assets/NOTICE.txt')==(r/'NOTICE.txt').read_bytes()
 assert z.read('assets/LICENSE')==(r/'LICENSE').read_bytes()
 assert sorted({n['path'].split('/')[1] for n in result['native']})==['arm64-v8a','x86','x86_64']
result['zipalign']=subprocess.check_output([str(zipalign),'-c','-P','16','-v','4',str(apk)],text=True)
(r/'evidence').mkdir(exist_ok=True);(r/'evidence/packaging.json').write_text(json.dumps(result,indent=2)+'\n');print('PASS 64-bit native 16 KiB alignment (32-bit x86 NDK runtime: 4 KiB), three ABIs, separate rlottie linkage, no permissions, complete notices')
