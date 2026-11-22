import struct,sys
def chunks(data,off,end):
    while off<end:
        cid=data[off:off+4]; size=struct.unpack('<I',data[off+4:off+8])[0]
        yield cid,off+8,size; off+=8+size+(size&1)
def load(path):
    d=open(path,'rb').read(); res={}
    for cid,o,s in chunks(d,12,len(d)):
        if cid==b'LIST':
            typ=d[o:o+4]
            for c2,o2,s2 in chunks(d,o+4,o+s): res[c2.decode()]=(o2,s2)
    return d,res
GEN={0:'startAddrsOffset',1:'endAddrsOffset',2:'startloopAddrsOffset',3:'endloopAddrsOffset',4:'startAddrsCoarse',8:'initialFilterFc',12:'endAddrsCoarse',17:'pan',21:'delayModEnv',33:'delayVolEnv',34:'attackVolEnv',35:'holdVolEnv',36:'decayVolEnv',37:'sustainVolEnv',38:'releaseVolEnv',41:'instrument',43:'keyRange',44:'velRange',45:'startloopAddrsCoarse',48:'initialAttenuation',50:'endloopAddrsCoarse',51:'coarseTune',52:'fineTune',53:'sampleID',54:'sampleModes',56:'scaleTuning',58:'overridingRootKey'}
def parse(path):
    d,r=load(path)
    def recs(name,fmt):
        o,s=r[name]; n=struct.calcsize(fmt); return [struct.unpack(fmt,d[o+i:o+i+n]) for i in range(0,s,n)]
    phdr=recs('phdr','<20sHHHIII'); pbag=recs('pbag','<HH'); pgen=recs('pgen','<HH')
    inst=recs('inst','<20sH'); ibag=recs('ibag','<HH'); igen=recs('igen','<HH')
    shdr=recs('shdr','<20sIIIIIBbHH')
    smpl=r['smpl']
    def gens(bags,genlist,i0,i1):
        zones=[]
        for b in range(i0,i1):
            g0=bags[b][0]; g1=bags[b+1][0]; z={}
            for gi in range(g0,g1):
                op,amt=genlist[gi]
                if op in (43,44): z[GEN[op]]=(amt&0xff,amt>>8)
                else: z[GEN.get(op,op)]= amt if op in (41,53,54,58) else struct.unpack('<h',struct.pack('<H',amt))[0]
            zones.append(z)
        return zones
    presets=[]
    for i in range(len(phdr)-1):
        name,prog,bank,bag=phdr[i][:4]
        presets.append(dict(name=name.split(b'\0')[0].decode(),prog=prog,bank=bank,zones=gens(pbag,pgen,bag,phdr[i+1][3])))
    insts=[]
    for i in range(len(inst)-1):
        insts.append(dict(name=inst[i][0].split(b'\0')[0].decode(),zones=gens(ibag,igen,inst[i][1],inst[i+1][1])))
    samples=[dict(name=s[0].split(b'\0')[0].decode(),start=s[1],end=s[2],ls=s[3],le=s[4],rate=s[5],key=s[6],corr=s[7]) for s in shdr]
    return d,smpl,presets,insts,samples
if __name__=='__main__':
    d,smpl,presets,insts,samples=parse(sys.argv[1])
    for p in presets:
        if p['bank']==0 and 71<=p['prog']<=79:
            print(p['prog'],p['name'])
            for z in p['zones']:
                if 'instrument' in z:
                    ins=insts[z['instrument']]; print('   inst',ins['name'])
                    for iz in ins['zones']:
                        if 'sampleID' in iz:
                            s=samples[iz['sampleID']]; print('      ',iz.get('keyRange'),s['name'],'root',iz.get('overridingRootKey',s['key']),'len %.2fs'%((s['end']-s['start'])/s['rate']),'loop %.2f-%.2fs'%((s['ls']-s['start'])/s['rate'],(s['le']-s['start'])/s['rate']),'rate',s['rate'],'mode',iz.get('sampleModes'))
