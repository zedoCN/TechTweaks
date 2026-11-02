#!/usr/bin/env python3
"""Run HBM automation checks through MCPFabric in the isolated GuidanceVerification world."""
import importlib.util
import json
from pathlib import Path
import time
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('guidance', ROOT / 'scripts/verify-guidance.py')
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
v.IMPORTS += ['net.minecraft.world.item.*', 'net.minecraft.resources.Identifier',
              'net.minecraft.core.registries.BuiltInRegistries',
              'net.neoforged.neoforge.capabilities.Capabilities',
              'net.neoforged.neoforge.transfer.item.ItemResource',
              'net.neoforged.neoforge.transfer.transaction.Transaction',
              'com.hbm.blockentity.machine.storage.*',
              'com.hbm.blockentity.machine.fusion.BlockEntityFusionPlasmaForge',
              'com.hbm.inventory.fluid.NTMFluids', 'com.hbm.items.ModItems',
              'com.hbm.blockentity.network.*']

HELPERS = '''
class F {
 ItemStack item(String id,int count){return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)),count);}
 BlockPos place(String id,int x,int y,int z){
  var pos=new BlockPos(x,y,z);var block=BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));var state=block.defaultBlockState();
  if(block instanceof com.hbm.blocks.multiblock.BlockMultiblockCore multi){
   state=state.setValue(com.hbm.blocks.multiblock.BlockMultiblockCore.FACING,Direction.NORTH);
   level.setBlockAndUpdate(pos,state);multi.fillSpace(level,pos,Direction.NORTH);
  }else level.setBlockAndUpdate(pos,state);
  return pos;
 }
 net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> port(BlockPos pos,Direction side){return level.getCapability(Capabilities.Item.BLOCK,pos,side);}
 int insert(BlockPos pos,Direction side,ItemStack stack,boolean commit){
  var h=port(pos,side);if(h==null)throw new IllegalStateException("missing item port "+pos+" "+side);
  try(var t=Transaction.openRoot()){int n=h.insert(ItemResource.of(stack),stack.getCount(),t);if(commit)t.commit();return n;}
 }
 int extract(BlockPos pos,Direction side,int slot,int count,boolean commit){
  var h=port(pos,side);if(h==null)throw new IllegalStateException("missing item port "+pos+" "+side);
  try(var t=Transaction.openRoot()){int n=h.extract(slot,h.getResource(slot),count,t);if(commit)t.commit();return n;}
 }
 void command(String text){server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),text);}
 com.hbm.inventory.fluid.tank.FluidTankNTM tank(BlockPos pos){
  var be=level.getBlockEntity(pos);return be instanceof BlockEntityMachineFluidTank m?m.tank:((BlockEntityMachineOrbus)be).tank;
 }
 com.hbm.blockentity.BlockEntityMachineBase machine(BlockPos pos){return (com.hbm.blockentity.BlockEntityMachineBase)level.getBlockEntity(pos);}
 void reset(BlockPos pos,net.minecraft.world.level.material.Fluid fluid,int amount){var m=machine(pos);m.clearContent();var t=tank(pos);t.setTankType(fluid);t.setFill(amount);}
 void use(BlockPos pos){
  player.teleportTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+2.5);
  var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
  player.gameMode.useItemOn(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,hit);
 }
}
var f=new F();
'''
REPORT = []


def run(body):
    return v.server(HELPERS + body)


def check(name, body):
    result = run(body)
    if result is not True:
        raise AssertionError((name, result))
    REPORT.append(name)
    print('PASS ' + name, flush=True)


def main():
    if '--saved' in sys.argv:
        check('saved power link removal and surviving branch persist after world reload', '''
         var a=new BlockPos(64,80,96);var b=new BlockPos(72,80,96);var c=new BlockPos(64,80,104);
         var g=com.hbm.api.energymk2.PowerGraph.get(level);var node=g.getNode(a.asLong());
         return node!=null && node.hasRemoteLinks() && !node.remoteLinks.contains(b.asLong()) && node.remoteLinks.contains(c.asLong());
        ''')
        check('saved pipe link removal and surviving branch persist after world reload', '''
         var a=new BlockPos(90,80,96);var b=new BlockPos(98,80,96);var c=new BlockPos(90,80,104);
         var g=com.hbm.api.fluidmk2.FluidPipeGraph.graphAt(level,a.asLong());var node=g==null?null:g.getNode(a.asLong());
         return node!=null && node.hasRemoteLinks() && !node.remoteLinks.contains(b.asLong()) && node.remoteLinks.contains(c.asLong());
        ''')
        path=ROOT/'runs/evidence/hbm-automation-verification.json'
        data=json.loads(path.read_text());data['checks']+=REPORT;data['reload_verified']=True
        path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
        print('HBM_SAVED_LINKS_VERIFICATION_PASS',len(REPORT),flush=True)
        return
    v.rpc('unsafe.javaScratch', {'body': '((net.minecraft.client.Minecraft)ctx.minecraft()).options.pauseOnLostFocus=false;return true;', 'mainThread': True})
    v.rpc('screen.close')
    print(run('''player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
     player.teleportTo(85.5,82,70.5);
     for(int x:new int[]{64,90,110})level.setBlockAndUpdate(new BlockPos(x,79,64),Blocks.AIR.defaultBlockState());
     f.place("hbm:fusion_plasma_forge",64,80,64);
     f.place("hbm:machine_fluidtank",90,80,64);
     f.place("hbm:machine_orbus",110,80,64);
     return "HBM multiblock fixtures ready";'''), flush=True)
    check('plasma forge accepts selected recipe inputs with transactional rollback', '''
     var p=new BlockPos(64,80,64);var m=(BlockEntityFusionPlasmaForge)level.getBlockEntity(p);
     m.clearContent();m.module.setRecipe("plsm.icfstructural");var r=m.module.getRecipe();
     if(r==null)return "recipe missing";
     for(int i=0;i<r.inputItem.length;i++){
      var stack=r.inputItem[i].displayStacks().getFirst().copyWithCount(r.inputItem[i].count());
      if(f.insert(p,Direction.DOWN,stack,false)!=stack.getCount() || !m.getItem(3+i).isEmpty())return "rollback failed "+i;
      if(f.insert(p,Direction.DOWN,stack,true)!=stack.getCount() || m.getItem(3+i).getCount()!=stack.getCount())return "insertion failed "+i;
     }
     return f.insert(p,Direction.DOWN,new ItemStack(Items.DIRT),true)==0;
    ''')
    check('forge protects recipe inputs and keeps native output extraction', '''
     var p=new BlockPos(64,80,64);var m=(BlockEntityFusionPlasmaForge)level.getBlockEntity(p);
     if(f.extract(p,Direction.DOWN,3,64,true)!=0)return false;
     m.setItem(15,new ItemStack(Items.DIAMOND,3));
     return f.extract(p,Direction.DOWN,15,3,false)==3 && m.getItem(15).getCount()==3
       && f.extract(p,Direction.DOWN,15,3,true)==3 && m.getItem(15).isEmpty();
    ''')
    for x, label in [(90, 'ordinary tank'), (110, 'magnetic tank')]:
        print(run(f'''var p=new BlockPos({x},80,64);f.reset(p,NTMFluids.DEUTERIUM,10000);return f.tank(p).getFill();'''))
        check(label + ' routes full and empty cells to separate lanes', f'''
         var p=new BlockPos({x},80,64);var m=f.machine(p);
         return f.insert(p,Direction.DOWN,new ItemStack(ModItems.CELL_EMPTY.get(),2),true)==2
          && m.getItem(4).getCount()==2 && m.getItem(2).isEmpty()
          && f.extract(p,Direction.DOWN,4,2,true)==0;
        ''')
        time.sleep(.25)
        check(label + ' fills cells through native ticks and output can be extracted', f'''
         var p=new BlockPos({x},80,64);var m=f.machine(p);
         return m.getItem(4).isEmpty() && m.getItem(5).getCount()==2
          && f.tank(p).getFill()==8000 && f.extract(p,Direction.DOWN,5,2,true)==2;
        ''')
        check(label + ' rejects wrong fluids and aborts full-container input cleanly', f'''
         var p=new BlockPos({x},80,64);f.reset(p,NTMFluids.DEUTERIUM,0);var m=f.machine(p);
         return f.insert(p,Direction.DOWN,new ItemStack(ModItems.CELL_UF6.get()),true)==0
          && f.insert(p,Direction.DOWN,new ItemStack(ModItems.CELL_DEUTERIUM.get(),2),false)==2
          && m.getItem(2).isEmpty()
          && f.insert(p,Direction.DOWN,new ItemStack(ModItems.CELL_DEUTERIUM.get(),2),true)==2;
        ''')
        time.sleep(.25)
        check(label + ' drains full cells and returns empties without loss', f'''
         var p=new BlockPos({x},80,64);var m=f.machine(p);
         return m.getItem(2).isEmpty() && m.getItem(3).is(ModItems.CELL_EMPTY.get())
          && m.getItem(3).getCount()==2 && f.tank(p).getFill()==2000;
        ''')
        # Native jetpacks are mutable items: leave a partly filled pack in its input lane.
        check(label + ' accepts jetpack native fuel but refuses wrong fuel', f'''
         var p=new BlockPos({x},80,64);f.reset(p,NTMFluids.WATER,1000);
         if(f.insert(p,Direction.DOWN,new ItemStack(ModItems.JETPACK_FLY.get()),true)!=0)return false;
         f.reset(p,NTMFluids.KEROSENE,1000);
         return f.insert(p,Direction.DOWN,new ItemStack(ModItems.JETPACK_FLY.get()),true)==1;
        ''')
        time.sleep(.25)
        check(label + ' retains partly filled jetpack when fuel runs out', f'''
         var p=new BlockPos({x},80,64);var m=f.machine(p);var item=ModItems.JETPACK_FLY.get();
         return m.getItem(4).is(item) && item.getFuel(m.getItem(4))==1000 && m.getItem(5).isEmpty()
          && f.extract(p,Direction.DOWN,4,1,true)==0;
        ''')
        run(f'var p=new BlockPos({x},80,64);f.tank(p).setFill(f.tank(p).getMaxFill());return true;')
        time.sleep(.25)
        check(label + ' moves fully charged jetpack to output with components intact', f'''
         var p=new BlockPos({x},80,64);var m=f.machine(p);var item=ModItems.JETPACK_FLY.get();
         return m.getItem(4).isEmpty() && m.getItem(5).is(item)
          && item.getFuel(m.getItem(5))==item.capacity(m.getItem(5))
          && f.extract(p,Direction.DOWN,5,1,false)==1 && !m.getItem(5).isEmpty();
        ''')
        for container in ['FLUID_TANK', 'CANISTER']:
            check(label + ' accepts empty ' + container, f'''
             var p=new BlockPos({x},80,64);var item=ModItems.{container}.get();
             f.reset(p,NTMFluids.KEROSENE,10000);
             return f.insert(p,Direction.DOWN,new ItemStack(item),true)==1;
            ''')
            time.sleep(.25)
            check(label + ' fills and routes ' + container + ' back for draining', f'''
             var p=new BlockPos({x},80,64);var m=f.machine(p);var item=ModItems.{container}.get();
             var full=m.getItem(5).copy();int amount=item.getFill(full);
             if(amount<=0 || !m.getItem(4).isEmpty() || f.tank(p).getFill()!=10000-amount)return false;
             m.setItem(5,ItemStack.EMPTY);
             return f.insert(p,Direction.DOWN,full,true)==1 && !m.getItem(2).isEmpty();
            ''')
            time.sleep(.25)
            check(label + ' returns empty ' + container + ' and conserves fluid', f'''
             var p=new BlockPos({x},80,64);var m=f.machine(p);var item=ModItems.{container}.get();
             return m.getItem(2).isEmpty() && m.getItem(3).is(item)
              && item.getFill(m.getItem(3))==0 && f.tank(p).getFill()==10000;
            ''')
        run(f'''
         var p=new BlockPos({x},80,64);f.reset(p,NTMFluids.KEROSENE,32000);var m=f.machine(p);
         var pack=new ItemStack(ModItems.JETPACK_FLY.get());pack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("pack_marker"));
         m.setItem(5,new ItemStack(Items.DIRT,64));
         if(f.insert(p,Direction.DOWN,pack,true)!=1)throw new IllegalStateException("pack refused");return true;
        ''')
        time.sleep(.25)
        check(label + ' full output holds charged jetpack without losing its name', f'''
         var m=f.machine(new BlockPos({x},80,64));var item=ModItems.JETPACK_FLY.get();var pack=m.getItem(4);
         return pack.is(item) && item.getFuel(pack)==item.capacity(pack)
          && pack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("pack_marker")
          && m.getItem(5).is(Items.DIRT) && m.getItem(5).getCount()==64;
        ''')
        run(f'f.machine(new BlockPos({x},80,64)).setItem(5,ItemStack.EMPTY);return true;')
        time.sleep(.25)
        check(label + ' freeing output releases exactly one fully charged named jetpack', f'''
         var m=f.machine(new BlockPos({x},80,64));var item=ModItems.JETPACK_FLY.get();
         return m.getItem(4).isEmpty() && m.getItem(5).is(item) && m.getItem(5).getCount()==1
          && m.getItem(5).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString().equals("pack_marker");
        ''')
    check('native wiring spool builds two virtual power branches', '''
     var a=f.place("hbm:red_pylon",64,80,96);var b=f.place("hbm:red_pylon",72,80,96);var c=f.place("hbm:red_pylon",64,80,104);
     player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.WIRING_RED_COPPER.get()));
     f.use(a);f.use(b);f.use(a);f.use(c);
     var g=com.hbm.api.energymk2.PowerGraph.get(level);
     return g.getNode(a.asLong()).remoteLinks.contains(b.asLong()) && g.getNode(a.asLong()).remoteLinks.contains(c.asLong());
    ''')
    check('cutter removes only selected power branch and splits the real network', '''
     var a=new BlockPos(64,80,96);var b=new BlockPos(72,80,96);var c=new BlockPos(64,80,104);
     player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(TechTweaks.LINK_CUTTER.get()));f.use(a);f.use(b);
     var g=com.hbm.api.energymk2.PowerGraph.get(level);
     return !g.getNode(a.asLong()).remoteLinks.contains(b.asLong()) && !g.getNode(b.asLong()).remoteLinks.contains(a.asLong())
      && g.getNode(a.asLong()).remoteLinks.contains(c.asLong()) && g.networkAt(a.asLong())==g.networkAt(c.asLong())
      && g.networkAt(a.asLong())!=g.networkAt(b.asLong())
      && ((BlockEntityPylonBase)level.getBlockEntity(a)).connections.length==1;
    ''')
    check('same endpoint cancels without cutting another power branch', '''
     var a=new BlockPos(64,80,96);f.use(a);f.use(a);
     return com.hbm.api.energymk2.PowerGraph.get(level).getNode(a.asLong()).remoteLinks.contains(new BlockPos(64,80,104).asLong())
      && !player.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    ''')
    check('native pipe wrench builds two virtual pipe branches', '''
     var a=f.place("hbm:pipe_anchor",90,80,96);var b=f.place("hbm:pipe_anchor",98,80,96);var c=f.place("hbm:pipe_anchor",90,80,104);
     player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.WRENCH.get()));
     f.use(a);f.use(b);f.use(a);f.use(c);
     var g=com.hbm.api.fluidmk2.FluidPipeGraph.graphAt(level,a.asLong());
     return g!=null && g.getNode(a.asLong()).remoteLinks.contains(b.asLong()) && g.getNode(a.asLong()).remoteLinks.contains(c.asLong());
    ''')
    check('cutter removes only selected pipe branch and updates both rendered endpoints', '''
     var a=new BlockPos(90,80,96);var b=new BlockPos(98,80,96);var c=new BlockPos(90,80,104);
     player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(TechTweaks.LINK_CUTTER.get()));f.use(a);f.use(b);
     var g=com.hbm.api.fluidmk2.FluidPipeGraph.graphAt(level,a.asLong());
     return !g.getNode(a.asLong()).remoteLinks.contains(b.asLong()) && !g.getNode(b.asLong()).remoteLinks.contains(a.asLong())
      && g.getNode(a.asLong()).remoteLinks.contains(c.asLong()) && g.networkAt(a.asLong())!=g.networkAt(b.asLong())
      && ((BlockEntityPipeAnchor)level.getBlockEntity(a)).connections.length==1
      && ((BlockEntityPipeAnchor)level.getBlockEntity(b)).connections.length==0;
    ''')
    check('mixed endpoint types cannot remove any link', '''
     var a=new BlockPos(90,80,96);f.use(a);f.use(new BlockPos(64,80,96));
     return com.hbm.api.fluidmk2.FluidPipeGraph.graphAt(level,a.asLong()).getNode(a.asLong()).remoteLinks.contains(new BlockPos(90,80,104).asLong())
      && !player.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    ''')
    check('unloaded saved endpoint is rejected without loading its chunk', '''
     var stack=player.getMainHandItem();var tag=new net.minecraft.nbt.CompoundTag();
     tag.putLong("tech_link_start",new BlockPos(1000000,80,1000000).asLong());
     tag.putString("tech_link_dimension",level.dimension().identifier().toString());tag.putString("tech_link_kind","pipe");
     stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
     int before=level.getChunkSource().getLoadedChunksCount();f.use(new BlockPos(90,80,96));
     return level.getChunkSource().getLoadedChunksCount()==before && !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    ''')
    check('saved endpoint from another dimension is rejected without cutting', '''
     var a=new BlockPos(90,80,96);var stack=player.getMainHandItem();f.use(a);
     var tag=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();tag.putString("tech_link_dimension","minecraft:the_nether");
     stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));f.use(new BlockPos(90,80,104));
     return com.hbm.api.fluidmk2.FluidPipeGraph.graphAt(level,a.asLong()).getNode(a.asLong()).remoteLinks.contains(new BlockPos(90,80,104).asLong());
    ''')
    for x, label in [(90, 'ordinary tank'), (110, 'magnetic tank')]:
        print(run(f'''
         var p=new BlockPos({x},80,64);f.reset(p,NTMFluids.DEUTERIUM,10000);
         var source=f.place("minecraft:barrel",{x},78,64);
         var box=(net.minecraft.world.Container)level.getBlockEntity(source);box.clearContent();box.setItem(0,new ItemStack(ModItems.CELL_EMPTY.get(),2));
         f.command("setblock {x} 79 64 pipez:item_pipe[has_data=true]{{ExtractingSides:[1b,0b,0b,0b,0b,0b]}}");
         return "real Pipez filling {label}";
        '''), flush=True)
        time.sleep(1.5)
        check(label + ' receives and fills containers from a real ticking Pipez pipe', f'''
         var m=f.machine(new BlockPos({x},80,64));
         return m.getItem(5).is(ModItems.CELL_DEUTERIUM.get()) && m.getItem(5).getCount()==2
          && ((net.minecraft.world.Container)level.getBlockEntity(new BlockPos({x},78,64))).isEmpty();
        ''')
        run(f'''level.setBlockAndUpdate(new BlockPos({x},79,64),Blocks.AIR.defaultBlockState());f.command("setblock {x} 79 64 pipez:item_pipe[has_data=true]{{ExtractingSides:[0b,1b,0b,0b,0b,0b]}}");return true;''')
        time.sleep(1.5)
        check(label + ' exports completed containers through a real ticking Pipez pipe', f'''
         var box=(net.minecraft.world.Container)level.getBlockEntity(new BlockPos({x},78,64));
         return f.machine(new BlockPos({x},80,64)).getItem(5).isEmpty()
          && box.getItem(0).is(ModItems.CELL_DEUTERIUM.get()) && box.getItem(0).getCount()==2;
        ''')
    print(run('''
     var p=new BlockPos(64,80,64);var m=(BlockEntityFusionPlasmaForge)level.getBlockEntity(p);m.clearContent();m.module.setRecipe("plsm.icfstructural");
     var source=f.place("minecraft:barrel",64,78,64);var box=(net.minecraft.world.Container)level.getBlockEntity(source);box.clearContent();
     var r=m.module.getRecipe();for(int i=0;i<r.inputItem.length;i++)box.setItem(i,r.inputItem[i].displayStacks().getFirst().copyWithCount(r.inputItem[i].count()));
     f.command("setblock 64 79 64 pipez:item_pipe[has_data=true]{ExtractingSides:[1b,0b,0b,0b,0b,0b]}");return "real Pipez feeding forge";
    '''), flush=True)
    time.sleep(3)
    check('real ticking Pipez inserts all three forge recipe ingredients in their correct slots', '''
     var m=(BlockEntityFusionPlasmaForge)level.getBlockEntity(new BlockPos(64,80,64));var r=m.module.getRecipe();
     for(int i=0;i<r.inputItem.length;i++)if(!r.inputItem[i].test(m.getItem(3+i)))return false;
     return ((net.minecraft.world.Container)level.getBlockEntity(new BlockPos(64,78,64))).isEmpty();
    ''')
    run('server.saveEverything(true,true,true);return true;')
    (ROOT / 'runs/evidence').mkdir(exist_ok=True)
    (ROOT / 'runs/evidence/hbm-automation-verification.json').write_text(json.dumps({'checks': REPORT}, ensure_ascii=False, indent=2) + '\n')
    print('HBM_AUTOMATION_VERIFICATION_PASS', len(REPORT), flush=True)


if __name__ == '__main__':
    main()
