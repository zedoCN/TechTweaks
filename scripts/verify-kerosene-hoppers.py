#!/usr/bin/env python3
"""Real hopper filling with the exact pinned kerosene used in production."""
import importlib.util,json,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('t',ROOT/'scripts/verify-hbm-feedback.py');t=importlib.util.module_from_spec(s);s.loader.exec_module(t)
for x,label in [(500,'tank'),(520,'orbus')]:
 for item in ['FLUID_TANK','CANISTER','JETPACK_FLY']:
  code=f'''
  var p=new BlockPos({x},80,64);player.teleportTo({x}.5,82,72.5);var m=f.machine(p);m.clearContent();f.tank(p).setTankTypeByIdentifier(NTMFluids.KEROSENE);f.tank(p).setFill(f.tank(p).getMaxFill());
  BlockPos q=null;Direction face=null;
  outer:for(var a:BlockPos.betweenClosed(p.offset(-3,0,-3),p.offset(3,4,3)))for(var d:Direction.values()){{
   if(d==Direction.DOWN||a.equals(p)||!level.getBlockState(a.relative(d)).isAir())continue;
   var owner=com.hbm.capability.NtmCapabilities.fullSurfaceCap(level,a,level.getBlockState(a),d,com.hbm.api.fluidmk2.IFluidHandlerMK2.class,com.hbm.capability.NtmCapabilities.CapRole.FLUID_IN);
   if(owner==m && f.port(a,d)!=null){{q=a.relative(d).immutable();face=d;break outer;}}
  }}
  if(q==null)return "no native hopper input face";
  level.setBlockAndUpdate(q,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,face.getOpposite()));
  ((net.minecraft.world.Container)level.getBlockEntity(q)).setItem(0,new ItemStack(ModItems.{item}.get()));
  var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("x",q.getX());tag.putInt("y",q.getY());tag.putInt("z",q.getZ());server.getCommandStorage().set(net.minecraft.resources.Identifier.parse("techtest:kerosene_hopper"),tag);return true;
  '''
  t.check(label+' pinned kerosene real hopper '+item+' fixture',code)
  t.eventually(label+' actual hopper fills and outputs '+item,f'''
   var m=f.machine(new BlockPos({x},80,64));var item=ModItems.{item}.get();var stack=m.getItem(5);
   return m.getItem(4).isEmpty() && stack.is(item) && item.getContent(stack).amount()>0 && f.tank(new BlockPos({x},80,64)).getFill()<f.tank(new BlockPos({x},80,64)).getMaxFill();
  ''')
  t.run('''var d=server.getCommandStorage().get(net.minecraft.resources.Identifier.parse("techtest:kerosene_hopper"));level.setBlockAndUpdate(new BlockPos(d.getIntOr("x",0),d.getIntOr("y",0),d.getIntOr("z",0)),Blocks.AIR.defaultBlockState());return true;''')
(ROOT/'runs/evidence/hbm-kerosene-hoppers-verification.json').write_text(json.dumps({'version':'0.1.8','checks':t.REPORT},ensure_ascii=False,indent=2)+'\n')
print('KEROSENE_HOPPERS_PASS',len(t.REPORT),flush=True)
