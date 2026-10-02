from pathlib import Path
import json,gzip
r=Path(__file__).resolve().parents[1]/'app/src/main/assets'
def prop(k):return {'a':0,'k':k}
def animated(a,b):return {'a':1,'k':[{'t':0,'s':a,'e':b,'o':{'x':0.33,'y':0},'i':{'x':0.67,'y':1}},{'t':59,'s':b}]}
def transform(p,rotation=0,scale=[100,100,100]):return {'o':prop(100),'r':prop(rotation),'p':prop(p),'a':prop([0,0,0]),'s':prop(scale)}
def shape(kind,p,size,color):return [{'ty':kind,'p':prop(p),'s':prop(size),'r':prop(18),'d':1},{'ty':'fl','c':prop(color),'o':prop(100),'r':1}]
def layer(n,shapes,ks):return {'ddd':0,'ind':n,'ty':4,'nm':'Vector '+str(n),'sr':1,'ks':ks,'ao':0,'shapes':shapes,'ip':0,'op':60,'st':0,'bm':0}
base={'v':'5.5.7','fr':30,'ip':0,'op':60,'w':512,'h':512,'nm':'Oritwig Orbit · synthetic vector fixture','ddd':0,'assets':[]}
k=transform([128,256,0]);k['p']=animated([128,256,0],[384,256,0]);k['r']=animated([0],[180])
base['layers']=[layer(1,shape('rc',[0,0],[112,112],[1,0.36,0.28,1]),k),layer(2,shape('el',[0,0],[66,66],[0.35,0.95,0.76,1]),transform([256,128,0])),layer(3,[{'ty':'el','p':prop([0,0]),'s':prop([300,300]),'d':1},{'ty':'st','c':prop([0.20,0.35,0.46,1]),'o':prop(100),'w':prop(4),'lc':2,'lj':2}],transform([256,256,0]))]
raw=json.dumps(base,separators=(',',':')).encode();(r/'orbit.json').write_bytes(raw);(r/'orbit.tgs').write_bytes(gzip.compress(raw,mtime=0))
base['w']=base['h']=64;base['nm']='Color and movement test';base['layers']=[layer(1,shape('rc',[0,0],[16,16],[1,0,0,1]),transform([16,32,0]))];base['layers'][0]['shapes'][0]['r']=prop(0);base['layers'][0]['ks']['p']=animated([16,32,0],[48,32,0]);(r/'test.json').write_text(json.dumps(base,separators=(',',':')))
(r/'invalid.json').write_text('{"bad":true}')
print('own synthetic JSON/TGS fixtures ready')
# A vector precomposition with three embedded layers and a separate foreground layer.
original=json.loads((r/'orbit.json').read_bytes())
pre=json.loads(json.dumps(original));pre['nm']='Orbit · embedded vector precomposition'
pre['assets']=[{'id':'orbit-vector','w':512,'h':512,'layers':original['layers']}]
ks=transform([256,256,0],scale=[85,85,100]);ks['a']=prop([256,256,0])
pre['layers']=[{'ddd':0,'ind':10,'ty':0,'nm':'Embedded orbit vectors','refId':'orbit-vector','sr':1,'ks':ks,'w':512,'h':512,'ip':0,'op':60,'st':0,'bm':0},layer(11,shape('el',[0,0],[26,26],[0.5,0.7,1,1]),transform([256,420,0]))]
payload=json.dumps(pre,separators=(',',':')).encode();(r/'precomp.json').write_bytes(payload);(r/'precomp.tgs').write_bytes(gzip.compress(payload,mtime=0))
