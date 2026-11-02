#!/usr/bin/env python3
"""Verify the fake-client sink through native HBM sends in GuidanceVerification only."""
import importlib.util,json,pathlib,time
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('guidance',ROOT/'scripts/verify-guidance.py')
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
checks=[]
def check(name,body):
 result=v.server(body)
 if result is not True:raise AssertionError((name,result))
 checks.append(name);print('PASS '+name,flush=True)

HELPERS='''
var profile=new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("TechLeakProbe".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"TechLeakProbe");
var info=net.minecraft.server.level.ClientInformation.createDefault();
var fake=dev.anvilcraft.rg.sd.entity.FakePlayer.create(server,level,profile,info,false);
var connection=new dev.anvilcraft.rg.sd.entity.FakeClientConnection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
var channel=(io.netty.channel.embedded.EmbeddedChannel)connection.channel();
if(!(channel instanceof cn.zedocn.tech.server.network.ClientlessChannel))throw new AssertionError("patched channel missing");
fake.connection=new dev.anvilcraft.rg.sd.entity.FakePlayerNetHandler(server,connection,fake,new net.minecraft.server.network.CommonListenerCookie(profile,0,info,false));
'''

check('10000 real HBM sends release buffers and leave no fake outbound queue',HELPERS+'''
 try{
  for(int i=0;i<10000;i++){
   var payload=new com.hbm.packet.toclient.PlayerAppearancePayload(player.getId(),(byte)0);
   var wire=payload.getCompiledBuffer();
   if(!payload.compiledIncludesWirePrefix())throw new AssertionError("HBM transport not ready");
   com.hbm.packet.PacketWire.sendTo(payload,fake);
   if(wire.refCnt()!=0)throw new AssertionError("retained HBM buffer "+i+":"+wire.refCnt());
  }
  return channel.outboundMessages().isEmpty() && channel.isOpen();
 }finally{channel.finishAndReleaseAll();}
''')

for phase in range(3):
 check('native fake join/send/remove cycle '+str(phase+1),HELPERS+'''
  server.getPlayerList().placeNewPlayer(connection,fake,new net.minecraft.server.network.CommonListenerCookie(profile,0,info,false));
  try{
   for(int i=0;i<1000;i++)com.hbm.packet.PacketWire.sendTo(new com.hbm.packet.toclient.PlayerAppearancePayload(player.getId(),(byte)0),fake);
   if(!channel.outboundMessages().isEmpty())return false;
  }finally{fake.kill();channel.finishAndReleaseAll();}
  return channel.outboundMessages().isEmpty();
 ''')
 deadline=time.monotonic()+5
 while time.monotonic()<deadline:
  if v.server('return server.getPlayerList().getPlayers().stream().noneMatch(p->p.getGameProfile().name().equals("TechLeakProbe"));'):break
  time.sleep(.1)
 else:raise AssertionError('native asynchronous fake removal did not finish')

v.server(HELPERS+'''
 try{com.hbm.packet.PacketWire.sendToPlayers(new com.hbm.packet.toclient.PlayerAppearancePayload(player.getId(),(byte)7),java.util.List.of(fake,player));}
 finally{channel.finishAndReleaseAll();}
 return true;
''')
try:
 deadline=time.monotonic()+10
 while time.monotonic()<deadline:
  result=v.rpc('unsafe.javaScratch',{'body':'var entity=((net.minecraft.client.Minecraft)ctx.minecraft()).getCameraEntity();return entity==null?-1:entity.getClass().getMethod("hbm$appearance").invoke(entity);','mainThread':True})['result']
  if result==7:break
  time.sleep(.2)
 else:raise AssertionError(('real client did not receive HBM appearance payload',result))
 checks.append('real client receives shared HBM payload alongside fake recipient');print('PASS '+checks[-1],flush=True)
finally:
 v.server('com.hbm.packet.PacketWire.sendTo(new com.hbm.packet.toclient.PlayerAppearancePayload(player.getId(),(byte)0),player);return true;')
check('no test fake players remain in the world','return server.getPlayerList().getPlayers().stream().noneMatch(p->p.getGameProfile().name().equals("TechLeakProbe"));')
(ROOT/'runs/evidence/fake-network-verification.json').write_text(json.dumps({'checks':checks,'passed':True},indent=2)+'\n')
print('FAKE_NETWORK_VERIFICATION_PASS',len(checks),flush=True)
