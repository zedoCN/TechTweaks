#!/usr/bin/env python3
"""Real armor attachments, native filling, transaction rollback, hopper and powered AE buses."""
import importlib.util,json,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('t',ROOT/'scripts/verify-hbm-feedback.py');t=importlib.util.module_from_spec(s);s.loader.exec_module(t)
t.a.v.IMPORTS += ['com.hbm.handler.ArmorModHandler','com.hbm.items.machine.IFluidContainerItem','com.hbm.items.ModDataComponents']
EXTRA='''
class G {
 ItemStack armor(IFluidContainerItem pack, boolean full){
  var a=new ItemStack(Items.DIAMOND_CHESTPLATE);a.setDamageValue(17);
  a.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Attachment regression"));
  var p=new ItemStack((Item)pack);if(!ArmorModHandler.isApplicable(a,p))throw new IllegalStateException("not applicable");
  if(full)pack.fill(p,NTMFluids.KEROSENE,Integer.MAX_VALUE,0);
  ArmorModHandler.applyMod(a,p);ArmorModHandler.applyMod(a,new ItemStack(ModItems.CLADDING_LEAD.get()));return a;
 }
 long fuel(ItemStack a,IFluidContainerItem pack){return pack.getContent(ArmorModHandler.mods(a).copy(ArmorModHandler.modSlot(new ItemStack((Item)pack)))).amount();}
 BlockPos[] input(BlockPos p){
  var m=f.machine(p);
  for(var a:BlockPos.betweenClosed(p.offset(-3,0,-3),p.offset(3,4,3)))for(var d:Direction.values()){
   if(d==Direction.DOWN||a.equals(p)||!level.getBlockState(a.relative(d)).isAir())continue;
   var owner=com.hbm.capability.NtmCapabilities.fullSurfaceCap(level,a,level.getBlockState(a),d,com.hbm.api.fluidmk2.IFluidHandlerMK2.class,com.hbm.capability.NtmCapabilities.CapRole.FLUID_IN);
   if(owner==m&&f.port(a,d)!=null)return new BlockPos[]{a.immutable(),new BlockPos(d.ordinal(),0,0)};
  }throw new IllegalStateException("no native input face");
 }
}
var g=new G();
'''
def run(code):return t.run(EXTRA+code)
def check(name,code):
 result=run(code)
 if result is not True:raise AssertionError((name,result))
 t.REPORT.append(name);print('PASS',name,flush=True)
def eventually(name,code,seconds=30):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  result=run(code)
  if result is True:t.REPORT.append(name);print('PASS',name,flush=True);return
  time.sleep(.3)
 raise AssertionError((name,result))

def parity():
 result=run("""int mods=0,pairs=0;
 for(var item:BuiltInRegistries.ITEM){
  if(!(item instanceof IFluidContainerItem c)||!c.machineFillable())continue;
  var armor=new ItemStack(Items.DIAMOND_CHESTPLATE);var mod=new ItemStack(item);
  if(!ArmorModHandler.isApplicable(armor,mod))continue;
  mods++;ArmorModHandler.applyMod(armor,mod);
  for(var fluid:BuiltInRegistries.FLUID){
   if(fluid==net.minecraft.world.level.material.Fluids.EMPTY)continue;
   var tank=new com.hbm.inventory.fluid.tank.FluidTankNTM(100000);
   tank.setTankTypeByIdentifier(fluid);tank.setFill(100000);
   var before=armor.copy();var slots=NonNullList.withSize(2,ItemStack.EMPTY);slots.set(0,armor.copy());
   boolean nativeResult=tank.unloadTank(0,1,slots);
   if(nativeResult!=cn.zedocn.tech.integration.TankAutomation.accepts(tank,4,armor)
     ||!ItemStack.matches(before,armor))return "mismatch "+BuiltInRegistries.ITEM.getKey(item)+" / "+BuiltInRegistries.FLUID.getKey(fluid);
   pairs++;
  }
 }
 return mods+","+pairs;""")
 counts=[int(x)for x in result.split(',')]
 assert counts[0]>=4 and counts[1]>=692,result
 report={'version':'0.1.9','attachment_types':counts[0],'registered_fluid_pairs':counts[1],'native_acceptance_agrees':True,'source_components_unchanged':True}
 (ROOT/'runs/evidence/hbm-armor-native-parity.json').write_text(json.dumps(report,indent=2)+'\n')
 print('NATIVE_ARMOR_PARITY_PASS',report,flush=True)

def main():
 t.a.v.rpc('screen.close');t.a.v.rpc('unsafe.javaScratch',{'body':'((net.minecraft.client.Minecraft)ctx.minecraft()).options.pauseOnLostFocus=false;return true;','mainThread':True})
 print(run('''player.teleportTo(620.5,82,72.5);for(int x:new int[]{600,640}){for(var p:BlockPos.betweenClosed(x-8,77,54,x+8,88,74))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());f.place(x==600?"hbm:machine_fluidtank":"hbm:machine_orbus",x,80,64);}return "armor fixtures";'''))
 for x,label in [(600,'tank'),(640,'orbus')]:
  for pack in ['JETPACK_FLY','JETPACK_BREAK']:
   prefix=f'var p=new BlockPos({x},80,64);var m=f.machine(p);var tank=f.tank(p);var pack=ModItems.{pack}.get();'
   name=label+' '+pack
   check(name+' native manual path and automated acceptance agree',prefix+'''
    m.clearContent();tank.setTankTypeByIdentifier(NTMFluids.KEROSENE);tank.setFill(tank.getMaxFill());
    var armor=g.armor(pack,false);var before=armor.copy();var scratch=new com.hbm.inventory.fluid.tank.FluidTankNTM(100000);scratch.setTankTypeByIdentifier(NTMFluids.KEROSENE);scratch.setFill(100000);
    var slots=NonNullList.withSize(2,ItemStack.EMPTY);slots.set(0,armor.copy());
    return scratch.unloadTank(0,1,slots)&&g.fuel(slots.get(0),pack)>0&&m.canPlaceItem(4,armor)&&ItemStack.matches(before,armor);
   ''')
   check(name+' nested input simulation preserves armor and tank',prefix+'''
    var a=g.armor(pack,false);var before=a.copy();int fill=tank.getFill();var port=g.input(p);var side=Direction.values()[port[1].getX()];var h=f.port(port[0],side);
    try(var outer=Transaction.openRoot()){try(var nested=Transaction.open(outer)){if(h.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(a),1,nested)!=1)return "nested rejected";nested.commit();}}
    return ItemStack.matches(a,before)&&m.getItem(4).isEmpty()&&tank.getFill()==fill;
   ''')
   check(name+' wrong fuel, pressure and plain armor rejected',prefix+'''
    var a=g.armor(pack,false);tank.setTankTypeByIdentifier(NTMFluids.WATER);boolean wrong=!m.canPlaceItem(4,a);tank.setTankTypeByIdentifier(NTMFluids.KEROSENE);tank.withPressure(1);boolean pressure=!m.canPlaceItem(4,a);tank.withPressure(0);tank.setFill(100);
    var plain=new ItemStack(Items.DIAMOND_CHESTPLATE);var cladding=plain.copy();ArmorModHandler.applyMod(cladding,new ItemStack(ModItems.CLADDING_LEAD.get()));
    return wrong&&pressure&&!m.canPlaceItem(4,plain)&&!m.canPlaceItem(4,cladding);
   ''')
   check(name+' real hopper input with only partial fuel',prefix+'''
    var port=g.input(p);var side=Direction.values()[port[1].getX()];var q=port[0].relative(side);
    level.setBlockAndUpdate(q,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,side.getOpposite()));
    ((net.minecraft.world.Container)level.getBlockEntity(q)).setItem(0,g.armor(pack,false));return true;
   ''')
   eventually(name+' partial attachment stays in input; no premature output',prefix+'''
    return tank.getFill()==0&&!m.getItem(4).isEmpty()&&g.fuel(m.getItem(4),pack)==100&&m.getItem(5).isEmpty();
   ''')
   check(name+' output obstruction does not discard chestplate',prefix+'''
    m.setItem(5,new ItemStack(Items.COBBLESTONE));tank.setFill(tank.getMaxFill());return true;
   ''')
   eventually(name+' native fill completes while blocked, all components retained',prefix+'''
    return ItemStack.matches(m.getItem(4),g.armor(pack,true))&&m.getItem(5).is(Items.COBBLESTONE);
   ''')
   check(name+' clear obstruction and install real output hopper',prefix+'''
    m.setItem(5,ItemStack.EMPTY);var q=p.below();level.setBlockAndUpdate(q,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,Direction.EAST));return true;
   ''')
   eventually(name+' complete chestplate extracted by real hopper intact',prefix+'''
    var h=(net.minecraft.world.Container)level.getBlockEntity(p.below());return ItemStack.matches(h.getItem(0),g.armor(pack,true))&&m.getItem(4).isEmpty()&&m.getItem(5).isEmpty();
   ''')
   # Clear nearby transport, then use an independently powered AE grid.
   check(name+' powered AE export fixture',prefix+'''
    for(var q:BlockPos.betweenClosed(p.offset(-4,-2,-4),p.offset(4,6,4)))if(level.getBlockState(q).is(Blocks.HOPPER))level.setBlockAndUpdate(q,Blocks.AIR.defaultBlockState());
    m.clearContent();tank.setFill(tank.getMaxFill());var b=p.below();ae.build(b,Direction.UP);ae.filter(b,Direction.UP,false,AEItemKey.of(g.armor(pack,false)));return true;
   ''')
   time.sleep(1.2)
   check(name+' exact component armor enters AE inventory',prefix+'return ae.seed(p.below(),AEItemKey.of(g.armor(pack,false)),1)==1;')
   eventually(name+' real AE export fills modified chestplate',prefix+'return ItemStack.matches(m.getItem(5),g.armor(pack,true))&&ae.amount(p.below(),AEItemKey.of(g.armor(pack,false)))==0;')
   check(name+' AE import uses filled armor components',prefix+'ae.filter(p.below(),Direction.UP,true,AEItemKey.of(g.armor(pack,true)));return true;')
   eventually(name+' real AE import recovers exact filled armor once',prefix+'return m.getItem(5).isEmpty()&&ae.amount(p.below(),AEItemKey.of(g.armor(pack,true)))==1;')
   run(prefix+'for(int i=1;i<=3;i++)level.setBlockAndUpdate(p.below(i),Blocks.AIR.defaultBlockState());return true;')
 (ROOT/'runs/evidence/hbm-armor-tanks-verification.json').write_text(json.dumps({'version':'0.1.9','checks':t.REPORT},ensure_ascii=False,indent=2)+'\n')
 print('ARMOR_TANKS_PASS',len(t.REPORT),flush=True)
if __name__=='__main__':
 import sys
 if '--parity-only' in sys.argv:parity()
 else:main();parity()
