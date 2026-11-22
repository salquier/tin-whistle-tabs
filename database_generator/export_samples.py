"""Export the piccolo of FluidR3_GM (MIT) as a compact sample bank for the app.

Usage: python3 export_samples.py FluidR3_GM.sf2 ../Irish_Whistle/app/src/main/assets/instruments/piccolo.bin
(FluidR3_GM.sf2: Debian/Ubuntu package fluid-soundfont-gm)."""
import sys,struct,numpy as np
import sf2  # same folder
d,smpl,presets,insts,samples=sf2.parse(sys.argv[1])
p=[p for p in presets if p['bank']==0 and p['prog']==72][0]
seen=set(); zones=[]
for z in p['zones']:
    if 'instrument' not in z: continue
    for iz in insts[z['instrument']]['zones']:
        if 'sampleID' not in iz: continue
        s=samples[iz['sampleID']]
        if '(R)' in s['name'] or s['name'] in seen: continue
        seen.add(s['name'])
        kr=iz.get('keyRange',(0,127)); root=iz.get('overridingRootKey',s['key'])
        o,_=smpl; data=np.frombuffer(d[o+2*s['start']:o+2*s['le']],dtype='<i2')  # up to loop end
        r=s['rate']; f32=data.astype(float)/32768
        seg=f32[int(0.03*r):int(0.35*r)]*np.hanning(int(0.35*r)-int(0.03*r))
        S=np.abs(np.fft.rfft(seg,16*len(seg))); f=np.fft.rfftfreq(16*len(seg),1/r)
        exp=440*2**((root-69)/12); band=(f>exp*0.55)&(f<exp*2.2); f0=f[band][np.argmax(S[band])]
        pitch=69+12*np.log2(f0/440)   # real pitch (fractional MIDI note) of the sample
        zones.append((kr[0],kr[1],pitch,s['ls']-s['start'],s['le']-s['start'],r,data,s['name']))
zones.sort(key=lambda z:z[0])
out=bytearray(b'TWSB'); out+=struct.pack('>ii',1,len(zones))
for lo,hi,pitch,ls,le,r,data,name in zones:
    out+=struct.pack('>iifiiii',lo,hi,float(pitch),ls,le,r,len(data)); out+=data.astype('>i2').tobytes()
    print(name,lo,hi,round(pitch,2),len(data))
open(sys.argv[2],'wb').write(out); print('bytes',len(out))
