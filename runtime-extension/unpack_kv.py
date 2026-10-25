"""Extract a Ghost .gkv capture without interpreting or changing its bytes."""
import argparse
import json
import struct
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('capture',type=Path)
p.add_argument('output',type=Path)
a=p.parse_args()
with a.capture.open('rb') as f:
    if f.read(8)!=b'GHOSTKV1':raise SystemExit('Not a GHOSTKV1 capture')
    length=struct.unpack('<Q',f.read(8))[0]
    manifest=json.loads(f.read(length))
    start=f.tell()
    a.output.mkdir(parents=True,exist_ok=False)
    (a.output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    for i,b in enumerate(manifest['buffers']):
        f.seek(start+b['payload_offset'])
        raw=f.read(b['allocated_bytes'])
        if len(raw)!=b['allocated_bytes']:raise SystemExit('Truncated capture')
        (a.output/f"buffer-{i}-layer-{b['layer']}.bin").write_bytes(raw)
print('Extracted layout and original allocation bytes to',a.output)
