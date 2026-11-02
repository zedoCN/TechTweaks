#!/usr/bin/env python3
"""Fresh, unconfigured tanks: first fluid transactions, containers, and real AE storage buses."""
import importlib.util,json,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('t',ROOT/'scripts/verify-hbm-feedback.py');t=importlib.util.module_from_spec(s);s.loader.exec_module(t)
v=t.a.v

def main():
 v.rpc('screen.close');v.rpc('unsafe.javaScratch',{'body':'((net.minecraft.client.Minecraft)ctx.minecraft()).options.pauseOnLostFocus=false;return true;','mainThread':True})
 for x,label in [(500,'tank'),(520,'orbus')]:
  p=f'new BlockPos({x},80,64)';bp=p+'.below()'
  t.check(label+' unconfigured first-fluid simulation and mixed-fluid rollback',f'''
   player.teleportTo({x}.5,82,70.5);var p={p};
   for(var q:BlockPos.betweenClosed(p.offset(-5,-4,-5),p.offset(5,6,5)))level.setBlockAndUpdate(q,Blocks.AIR.defaultBlockState());
   var block=BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("hbm:{'machine_fluidtank' if label=='tank' else 'machine_orbus'}"));
   var facing=Direction.{'WEST' if label=='tank' else 'NORTH'};level.setBlockAndUpdate(p,block.defaultBlockState().setValue(com.hbm.blocks.multiblock.BlockMultiblockCore.FACING,facing));((com.hbm.blocks.multiblock.BlockMultiblockCore)block).fillSpace(level,p,facing);
   var tank=f.tank(p);tank.resetTank();f.machine(p).clearContent();
   var h=level.getCapability(Capabilities.Fluid.BLOCK,p,Direction.DOWN);
   try(var tx=Transaction.openRoot()){{if(h.insert(FluidResource.of(NTMFluids.DEUTERIUM),1000,tx)!=1000)return "first fluid rejected";
    if(h.insert(FluidResource.of(NTMFluids.KEROSENE),1000,tx)!=0)return "mixed fluid accepted";}}
   return tank.getDeclaredFluid()==null && tank.getFill()==0;
  ''')
  t.check(label+' nested rollback and first fluid committed exactly once',f'''
   var p={p};var h=level.getCapability(Capabilities.Fluid.BLOCK,p,Direction.DOWN);
   try(var tx=Transaction.openRoot()){{if(h.insert(FluidResource.of(NTMFluids.DEUTERIUM),1000,tx)!=1000)return false;
    try(var sub=Transaction.open(tx)){{if(h.insert(FluidResource.of(NTMFluids.DEUTERIUM),500,sub)!=500)return false;}}
    tx.commit();}}
   return f.tank(p).getFill()==1000 && f.tank(p).getFluid()==NTMFluids.DEUTERIUM;
  ''')
  t.check(label+' rejects wrong fluid after first-fluid selection',f'''
   var h=level.getCapability(Capabilities.Fluid.BLOCK,{p},Direction.DOWN);try(var tx=Transaction.openRoot()){{return h.insert(FluidResource.of(NTMFluids.KEROSENE),1000,tx)==0;}}
  ''')
  t.check(label+' fresh tank accepts full container with simulation rollback',f'''
   var p={p};f.tank(p).resetTank();f.machine(p).clearContent();
   if(f.insert(p,Direction.DOWN,new ItemStack(ModItems.CELL_DEUTERIUM.get()),false)!=1)return false;
   if(!f.machine(p).getItem(2).isEmpty() || f.tank(p).getDeclaredFluid()!=null)return false;
   return f.insert(p,Direction.DOWN,new ItemStack(ModItems.CELL_DEUTERIUM.get()),true)==1;
  ''')
  t.eventually(label+' native tick selects full-container fluid and returns empty',f'''
   var m=f.machine({p});return f.tank({p}).getFill()==1000 && f.tank({p}).getFluid()==NTMFluids.DEUTERIUM
    && m.getItem(2).isEmpty() && m.getItem(3).is(ModItems.CELL_EMPTY.get());
  ''')
  t.check(label+' empty container queues before any fluid arrives',f'''
   var p={p};f.tank(p).resetTank();f.machine(p).clearContent();return f.insert(p,Direction.DOWN,new ItemStack(ModItems.CELL_EMPTY.get()),true)==1 && f.tank(p).getDeclaredFluid()==null;
  ''')
  t.check(label+' first fluid can enter a tank with queued empty container',f'''
   var h=level.getCapability(Capabilities.Fluid.BLOCK,{p},Direction.DOWN);try(var tx=Transaction.openRoot()){{int n=h.insert(FluidResource.of(NTMFluids.DEUTERIUM),1000,tx);tx.commit();return n==1000;}}
  ''')
  t.eventually(label+' queued cell fills after first-fluid arrival',f'return f.machine({p}).getItem(5).is(ModItems.CELL_DEUTERIUM.get()) && f.tank({p}).getFill()==0;')
  t.check(label+' real powered AE storage bus fixture, without storage disks',f'''
   var p={p};f.tank(p).resetTank();f.machine(p).clearContent();var m=f.machine(p);
   if(m instanceof BlockEntityMachineFluidTank z){{while(z.mode!=1)z.cycleMode();}}else ((BlockEntityMachineOrbus)m).setMode(1);
   var b=p.below();ae.build(b,Direction.UP);level.setBlockAndUpdate(b.below(2),Blocks.AIR.defaultBlockState());
   ae.host(b).addPart(AEParts.STORAGE_BUS.asItem(),Direction.UP,player);return true;
  ''')
  time.sleep(1.2)
  t.eventually(label+' AE storage bus first-fluid simulation leaves tank untyped',f'''
   var p={p};var inv=ae.inv(p.below());long n=inv.insert(AEFluidKey.of(NTMFluids.DEUTERIUM),1000,Actionable.SIMULATE,IActionSource.ofPlayer(player));
   return n==1000 && f.tank(p).getFill()==0 && f.tank(p).getDeclaredFluid()==null;
  ''')
  t.check(label+' actual AE storage bus inserts first fluid',f'''
   var p={p};var inv=ae.inv(p.below());var key=AEFluidKey.of(NTMFluids.DEUTERIUM);var src=IActionSource.ofPlayer(player);
   if(inv.insert(key,1000,Actionable.MODULATE,src)!=1000 || f.tank(p).getFill()!=1000)return "insert failed";
   return true;
  ''')
  t.eventually(label+' AE storage bus observes first fluid',f'return ae.amount({bp},AEFluidKey.of(NTMFluids.DEUTERIUM))>0;')
  t.check(label+' AE storage bus extracts first fluid after monitor refresh',f'''
   var p={p};int before=f.tank(p).getFill();long n=ae.inv(p.below()).extract(AEFluidKey.of(NTMFluids.DEUTERIUM),1000,Actionable.MODULATE,IActionSource.ofPlayer(player));return n>0 && f.tank(p).getFill()==before-n;
  ''')
  t.check(label+' AE storage bus inserts full cells into unconfigured tank',f'''
   var p={p};f.tank(p).resetTank();f.machine(p).clearContent();var inv=ae.inv(p.below());var key=AEItemKey.of(ModItems.CELL_DEUTERIUM.get());var src=IActionSource.ofPlayer(player);
   if(inv.insert(key,2,Actionable.SIMULATE,src)!=2 || f.tank(p).getDeclaredFluid()!=null)return "simulation";
   return inv.insert(key,2,Actionable.MODULATE,src)==2;
  ''')
  t.eventually(label+' AE bus observes returned empty cells after native drain',f'''
   var p={p};return f.tank(p).getFill()==2000 && ae.amount(p.below(),AEItemKey.of(ModItems.CELL_EMPTY.get()))==2;
  ''')
  t.check(label+' AE storage bus extracts returned cells',f'''
   var p={p};var src=IActionSource.ofPlayer(player);return ae.inv(p.below()).extract(AEItemKey.of(ModItems.CELL_EMPTY.get()),2,Actionable.MODULATE,src)==2 && f.machine(p).getItem(3).isEmpty();
  ''')
 (ROOT/'runs/evidence/hbm-untyped-tanks-verification.json').write_text(json.dumps({'version':'0.1.8','checks':t.REPORT},ensure_ascii=False,indent=2)+'\n')
 print('HBM_UNTYPED_TANKS_PASS',len(t.REPORT),flush=True)
if __name__=='__main__':main()
