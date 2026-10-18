#!/usr/bin/env python3
"""Check a running, isolated GuidanceVerification client; never connects to production."""
import json,pathlib,urllib.request,time
ROOT=pathlib.Path(__file__).resolve().parents[1]; CFG=json.loads((ROOT/'runs/guidance-client/config/mcpfabric.config.json').read_text())
def rpc(method,params=None):
 req=urllib.request.Request(f'http://127.0.0.1:{CFG["port"]}/rpc',data=json.dumps({'method':method,'params':params or {}}).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+CFG['token']})
 with urllib.request.urlopen(req,timeout=45) as r:result=json.load(r)
 if not result.get('ok'):raise RuntimeError(result)
 return result['result']
IMPORTS=['java.util.*','java.util.concurrent.TimeUnit','net.minecraft.server.MinecraftServer','net.minecraft.core.*','net.minecraft.world.level.block.*','net.minecraft.world.level.block.state.properties.*','net.minecraft.world.entity.*','net.minecraft.world.phys.*','cn.zedocn.tech.*','com.hbm.entity.projectile.EntityMeteor','com.hbm.handler.BossSpawnHandler']
def server(body):
 code='''var server=(MinecraftServer)ctx.server();if(server==null || !server.isSingleplayer() || !server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().endsWith("GuidanceVerification"))throw new IllegalStateException("isolated GuidanceVerification required");return server.submit(()->{try{var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();'''+body+'''}catch(Exception ex){throw new RuntimeException(ex);}}).get(30,TimeUnit.SECONDS);'''
 return rpc('unsafe.javaScratch',{'imports':IMPORTS,'body':code,'mainThread':False})['result']

REPORT=[]
def check(name,body):
 result=server(body)
 if result is not True:raise AssertionError((name,result))
 REPORT.append(name);print('PASS '+name,flush=True)
ROD='var pos=new BlockPos(0,80,0);'
FIND=ROD+'''var find=MeteorGuidance.class.getDeclaredMethod("nearestRod",net.minecraft.server.level.ServerLevel.class,double.class,double.class);find.setAccessible(true);'''

def main():
 rpc('unsafe.javaScratch',{'body':'((net.minecraft.client.Minecraft)ctx.minecraft()).options.pauseOnLostFocus=false;return true;','mainThread':True})
 rpc('screen.close')
 print(server(ROD+'''player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.teleportTo(.5,81,.5);for(var e:level.getAllEntities())if(e instanceof EntityMeteor)e.discard();level.setBlockAndUpdate(pos.below(),Blocks.BEDROCK.defaultBlockState());level.setBlockAndUpdate(pos,TechTweaks.METEOR_ROD.get().defaultBlockState().setValue(LightningRodBlock.FACING,Direction.UP));return "fixture ready";'''))
 check('upright exposed rod and exact 256 block boundary',FIND+'return pos.equals(find.invoke(null,level,256.5,.5)) && find.invoke(null,level,257.5,.5)==null && find.invoke(null,level,256.5,1.5)==null;')
 check('glass roof blocks guidance',FIND+'''level.setBlockAndUpdate(pos.above(3),Blocks.GLASS.defaultBlockState());boolean blocked=find.invoke(null,level,.5,.5)==null;level.setBlockAndUpdate(pos.above(3),Blocks.AIR.defaultBlockState());return blocked && pos.equals(find.invoke(null,level,.5,.5));''')
 check('waterlogged and sideways rods are inactive',FIND+'''var state=level.getBlockState(pos);level.setBlockAndUpdate(pos,state.setValue(LightningRodBlock.WATERLOGGED,true));boolean wet=find.invoke(null,level,.5,.5)==null;level.setBlockAndUpdate(pos,state.setValue(LightningRodBlock.FACING,Direction.EAST));boolean sideways=find.invoke(null,level,.5,.5)==null;level.setBlockAndUpdate(pos,state);return wet && sideways;''')
 check('unloaded search never forces chunks',FIND+'''int before=level.getChunkSource().getLoadedChunksCount();boolean absent=find.invoke(null,level,100000.5,100000.5)==null;return absent && before==level.getChunkSource().getLoadedChunksCount();''')
 check('guide preserves safe flag and vertical speed while clearing drift','''for(boolean safe:new boolean[]{false,true}){var m=new EntityMeteor(level);m.safe=safe;m.snapTo(200.5,384,.5,25,30);m.setDeltaMovement(.4,-2.5,-.2);if(MeteorGuidance.guide(m)!=m || m.safe!=safe || m.getX()!=.5 || m.getZ()!=.5 || !m.getDeltaMovement().equals(new Vec3(0,-2.5,0)))return false;}return true;''')
 check('outside range preserves original trajectory','''var m=new EntityMeteor(level);m.snapTo(257.5,384,.5,0,0);var velocity=new Vec3(.4,-2.5,-.2);m.setDeltaMovement(velocity);MeteorGuidance.guide(m);return m.getX()==257.5 && m.getDeltaMovement().equals(velocity);''')
 check('direct HBM spawn bypasses natural guidance','''BossSpawnHandler.spawnMeteorAtPlayer(player,false);boolean ok=false;for(var e:level.getAllEntities())if(e instanceof EntityMeteor m){ok=m.getX()!=.5 || m.getZ()!=.5 || m.getDeltaMovement().x!=0 || m.getDeltaMovement().z!=0;m.discard();}return ok;''')
 check('actual HBM natural update redirects one meteor','''var field=com.hbm.data.DataGroups.Param.class.getDeclaredField("value");field.setAccessible(true);var chance=com.hbm.data.WorldData.METEOR_STRIKE_CHANCE;var previous=field.get(chance);var shower=BossSpawnHandler.class.getDeclaredField("meteorShower");shower.setAccessible(true);int old=shower.getInt(null);try{field.set(chance,1);shower.setInt(null,0);var method=BossSpawnHandler.class.getDeclaredMethod("meteorUpdate",net.minecraft.server.level.ServerLevel.class);method.setAccessible(true);method.invoke(null,level);}finally{field.set(chance,previous);shower.setInt(null,old);}int count=0;for(var e:level.getAllEntities())if(e instanceof EntityMeteor m){count++;if(m.getX()!=.5 || m.getZ()!=.5 || m.safe || !m.getDeltaMovement().equals(new Vec3(0,-2.5,0)))return false;}return count==1;''')
 deadline=time.monotonic()+20
 while time.monotonic()<deadline:
  if server('for(var e:level.getAllEntities())if(e instanceof EntityMeteor)return false;return true;'):break
  time.sleep(.5)
 else:raise AssertionError('natural meteor did not impact')
 check('real natural impact hits rod and destroys it',ROD+'return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.BEDROCK);')
 check('destroyed rod no longer attracts meteors',FIND+'return find.invoke(null,level,.5,.5)==null;')
 print(server('''var pos=new BlockPos(8,80,0);level.setBlockAndUpdate(pos,TechTweaks.PROTECTION.get().defaultBlockState());var be=(ControllerBlockEntity)level.getBlockEntity(pos);be.radius=32;for(int i=0;i<40;i++)be.action(2);be.energy.set(Balance.ENERGY_CAPACITY);return "controller max="+be.radius;'''))
 time.sleep(1.2)
 check('256 radius consumes power and activates real controller','''var be=(ControllerBlockEntity)level.getBlockEntity(new BlockPos(8,80,0));return be.radius==256 && be.protectedRadius==256 && be.protectedUntil>level.getGameTime() && be.energy.getAmountAsLong()<=Balance.ENERGY_CAPACITY-131072;''')
 check('natural monster boundary blocked, outside and spawner unaffected','''var be=(ControllerBlockEntity)level.getBlockEntity(new BlockPos(8,80,0));var mob=new net.minecraft.world.entity.monster.zombie.Zombie(level);for(var reason:List.of(EntitySpawnReason.NATURAL,EntitySpawnReason.SPAWNER)){for(double distance:new double[]{256,256.01}){mob.setPos(8.5+distance,80.5,.5);var event=new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(mob,level,reason,null);AreaProtection.checkSpawn(event);boolean failed=event.getResult()==net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL;if(failed!=(reason==EntitySpawnReason.NATURAL && distance==256))return false;}}return true;''')
 check('unpowered controller expires','''var be=(ControllerBlockEntity)level.getBlockEntity(new BlockPos(8,80,0));be.energy.set(0);be.protectedUntil=level.getGameTime();var mob=new net.minecraft.world.entity.monster.zombie.Zombie(level);mob.setPos(8.5,80.5,.5);var event=new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(mob,level,EntitySpawnReason.NATURAL,null);AreaProtection.checkSpawn(event);return event.getResult()!=net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL;''')
 check('new and existing radii survive real NBT round trip','''var be=(ControllerBlockEntity)level.getBlockEntity(new BlockPos(8,80,0));for(int radius:new int[]{16,32,256}){be.radius=radius;var tag=be.saveWithFullMetadata(level.registryAccess());var restored=(ControllerBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),tag,level.registryAccess());if(restored==null || restored.radius!=radius)return false;}return true;''')
 print(server('''for(var pos:BlockPos.betweenClosed(5,79,-2,11,79,5))level.setBlockAndUpdate(pos,Blocks.BEDROCK.defaultBlockState());player.teleportTo(8.5,80,2.5);var be=(ControllerBlockEntity)level.getBlockEntity(new BlockPos(8,80,0));be.radius=248;player.openMenu(be);return "menu ready";'''))
 time.sleep(.3)
 rpc('screen.clickWidget',{'widgetPath':'2'})
 time.sleep(.3)
 check('real client increase button reaches 256 on server','''var be=(ControllerBlockEntity)level.getBlockEntity(new BlockPos(8,80,0));return be.radius==256 && player.containerMenu.getClass().getSimpleName().equals("ControllerMenu");''')
 rpc('screen.close')
 width=rpc('unsafe.javaScratch',{'body':'return ((net.minecraft.client.Minecraft)ctx.minecraft()).font.width("半径 256 格 · 131072 FE/s");','mainThread':True})['result']
 if width>160:raise AssertionError(('UI overflow',width))
 REPORT.append('range and power text fits UI')
 (ROOT/'runs/evidence/guidance-verification.json').write_text(json.dumps({'checks':REPORT,'uiWidth':width,'meteorRadius':256,'protectionRadius':256},ensure_ascii=False,indent=2)+'\n')
 print('GUIDANCE_VERIFICATION_PASS',len(REPORT))

if __name__=='__main__':main()
