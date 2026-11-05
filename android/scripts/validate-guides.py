"""Validate stable IDs, references, variants, vector paths and fixed capture policy before building."""
from pathlib import Path
import json,math
root=Path(__file__).resolve().parents[1];assets=root/'app/src/main/assets'
manifest=json.loads((assets/'press-camera.manifest.json').read_text());assert manifest['schemaVersion']==1
policy=manifest['camera'];assert policy['ratio']==1.5 and policy['savePolicy']=='RAW+JPG';assert policy['modes']==['자동','프로'] and policy['defaultMode']=='자동';assert not policy['editingEnabled'] and not policy['presetManagementEnabled']
catalog=json.loads((assets/manifest['content']['catalog']).read_text());layouts=json.loads((assets/manifest['content']['layouts']).read_text());preset=json.loads((assets/manifest['content']['preset']).read_text())
assert catalog['schemaVersion']==layouts['schemaVersion']==preset['schemaVersion']==1
layouts['layouts']={}
for gid,path in layouts['layoutFiles'].items():
 layout=json.loads((assets/path).read_text());assert layout['schemaVersion']==1 and layout['layoutId']==gid
 layouts['layouts'][gid]=layout['variants']
ids=[g['id'] for g in catalog['guides']];assert len(set(ids))==len(ids)
cats=[c['id'] for c in catalog['categories']];assert len(set(cats))==len(cats)
assert preset['id']=='press-clean' and preset['grain']==preset['bloom']==preset['vignette']==0
assert 0<=preset['detail']<=.3
for c in catalog['categories']:
 assert c['name'] and c['group'] and isinstance(c['keywords'],list)
 for key in ['primary','extra']:
  assert len(set(c[key]))==len(c[key])
  assert all(x in ids for x in c[key])
for g in catalog['guides']:
 assert g['tip'] and g['name'] and g['defaultPeople'] in g['peopleOptions']
 variants=layouts['layouts'][g['layoutId']]
 for n in g['peopleOptions']:
  for prop in (['individual','shared'] if g['id']=='held-prop' else ['individual']):
   v=variants[f'{n}:{prop}']
   for orientation in ['portrait','landscape']:
    for s in v[orientation]:
     assert s['type'] in ['rect','ellipse','path','text']
     if s['type']=='path':
      sizes={'M':2,'L':2,'Q':4,'C':6,'Z':0}
      for cmd in s['commands']:
       assert cmd[0] in sizes and len(cmd)==sizes[cmd[0]]+1
       assert all(isinstance(x,(int,float)) and math.isfinite(x) for x in cmd[1:])
     else:
      assert all(math.isfinite(v) for v in s.values() if isinstance(v,(int,float)))
print(f'PASS: capture policy, {len(cats)} categories, {len(ids)} guides, keyword references, all person/prop/orientation variants, vector commands')
