#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import shutil
r=Path(__file__).resolve().parents[1]
a=r/'app/src/main/assets'
shutil.copy2(r/'NOTICE.txt',a/'NOTICE.txt')
shutil.copy2(r/'LICENSE',a/'LICENSE')
(a/'licenses').mkdir(exist_ok=True)
for p in sorted((r/'provenance/licenses').iterdir()):
 if p.is_file():shutil.copy2(p,a/'licenses'/p.name)
print('Offline notices synchronized')
