#!/usr/bin/env python3
"""Regression checks for real outer ports, ticking hoppers/AE buses and NTM recipe grids."""
import importlib.util
import json
from pathlib import Path
import time

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('old', ROOT / 'scripts/verify-hbm-automation.py')
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)
a.v.IMPORTS += ['com.hbm.blockentity.machine.fusion.BlockEntityFusionBreeder',
    'net.neoforged.neoforge.transfer.fluid.FluidResource',
    'appeng.api.stacks.*', 'appeng.api.config.Actionable', 'appeng.core.definitions.*',
    'appeng.api.parts.IPartHost', 'appeng.api.networking.security.IActionSource']

HELPERS = '''
class A {
 IPartHost host(BlockPos p){return (IPartHost)level.getBlockEntity(p);}
 appeng.parts.automation.IOBusPart bus(BlockPos p,Direction side,boolean importing){
  var h=host(p);h.removePartFromSide(side);
  return importing?h.addPart(AEParts.IMPORT_BUS.asItem(),side,player):h.addPart(AEParts.EXPORT_BUS.asItem(),side,player);
 }
 void build(BlockPos p,Direction side){
  level.setBlockAndUpdate(p,AEBlocks.CABLE_BUS.block().defaultBlockState());
  host(p).addPart(AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT),null,player);
  level.setBlockAndUpdate(p.relative(side.getOpposite()),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
  var dp=p.relative(side.getOpposite(),2);level.setBlockAndUpdate(dp,AEBlocks.DRIVE.block().defaultBlockState());
  var drive=(appeng.blockentity.storage.DriveBlockEntity)level.getBlockEntity(dp);
  drive.getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_1K.stack());
  drive.getInternalInventory().setItemDirect(1,AEItems.FLUID_CELL_1K.stack());
 }
 appeng.api.storage.MEStorage inv(BlockPos p){return host(p).getPart(null).getGridNode().getGrid().getStorageService().getInventory();}
 long amount(BlockPos p,AEKey key){return inv(p).getAvailableStacks().get(key);}
 long seed(BlockPos p,AEKey key,long n){return inv(p).insert(key,n,Actionable.MODULATE,IActionSource.ofPlayer(player));}
 void filter(BlockPos p,Direction side,boolean importing,AEKey key){bus(p,side,importing).getConfig().setStack(0,new GenericStack(key,1));}
}
var ae=new A();
'''

REPORT = []

def run(code):
    return a.run(HELPERS + code)

def check(name, code):
    value = run(code)
    if value is not True:
        raise AssertionError((name, value))
    REPORT.append(name)
    print('PASS', name, flush=True)

def eventually(name, code, seconds=30):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        value = run(code)
        if value is True:
            REPORT.append(name)
            print('PASS', name, flush=True)
            return
        time.sleep(.3)
    raise AssertionError((name, value))

def machine_buses():
    for x, dx, dz, face, toward, kind in [(300,0,-5,'NORTH','SOUTH','forge'),(360,-1,0,'WEST','EAST','breeder')]:
        bp=f'new BlockPos({x+dx},80,{64+dz}).relative(Direction.{face})'
        check(kind+' powered AE export grid on shell',f'''
         player.teleportTo({x}.5,82,72.5);
         var p=new BlockPos({x},80,64);var m=f.machine(p);m.clearContent();
         var tanks=m instanceof BlockEntityFusionPlasmaForge g?g.getReceivingTanks():((BlockEntityFusionBreeder)m).tanks;
         tanks[0].setFill(0);tanks[0].setTankType(NTMFluids.DEUTERIUM);
         if(m instanceof BlockEntityFusionPlasmaForge g){{var recipe=(com.hbm.inventory.recipes.PlasmaForgeRecipe)server.getRecipeManager().getRecipes().stream().map(h->h.value()).filter(v->v instanceof com.hbm.inventory.recipes.PlasmaForgeRecipe r && r.inputFluid.length>0).findFirst().get();g.module.setRecipe(recipe.getInternalName());var r=g.module.getRecipe();tanks[0].setTankType(r.inputFluid[0].type());g.booster=0;}}
         ae.build({bp},Direction.{toward});return true;
        ''')
        time.sleep(1.2)
        check(kind+' AE input filter and network stock',f'''
         var m=f.machine(new BlockPos({x},80,64));int slot=m instanceof BlockEntityFusionPlasmaForge?2:1;ItemStack input=ItemStack.EMPTY;
         for(var i:BuiltInRegistries.ITEM){{var s=new ItemStack(i);if(m.canPlaceItem(slot,s)){{input=s;break;}}}}
         if(input.isEmpty())return "no valid input";var key=AEItemKey.of(input);ae.filter({bp},Direction.{toward},false,key);return ae.seed({bp},key,1)==1;
        ''')
        eventually(kind+' real AE bus inserts native input or booster',f'''
         var m=f.machine(new BlockPos({x},80,64));return m instanceof BlockEntityFusionPlasmaForge g?g.booster>0:!m.getItem(1).isEmpty();
        ''')
        check(kind+' AE fluid filter and stock',f'''
         var m=f.machine(new BlockPos({x},80,64));var tanks=m instanceof BlockEntityFusionPlasmaForge g?g.getReceivingTanks():((BlockEntityFusionBreeder)m).tanks;
         var key=AEFluidKey.of(tanks[0].getFluid());ae.filter({bp},Direction.{toward},false,key);return ae.seed({bp},key,1000)==1000;
        ''')
        eventually(kind+' real AE export bus inserts fluid through shell',f'''
         var m=f.machine(new BlockPos({x},80,64));var tanks=m instanceof BlockEntityFusionPlasmaForge g?g.getReceivingTanks():((BlockEntityFusionBreeder)m).tanks;
         return tanks[0].getFill()==1000;
        ''')
        run(f'for(int i=0;i<3;i++)level.setBlockAndUpdate({bp}.relative(Direction.{toward}.getOpposite(),i),Blocks.AIR.defaultBlockState());return true;')

def main():
    a.v.rpc('screen.close')
    a.v.rpc('unsafe.javaScratch', {'body': '((net.minecraft.client.Minecraft)ctx.minecraft()).options.pauseOnLostFocus=false;return true;', 'mainThread': True})
    print(run('''player.teleportTo(220.5,82,72.5);for(int x:new int[]{180,240,300,360})for(var p:BlockPos.betweenClosed(x-8,77,54,x+8,87,74))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
     f.place("hbm:machine_fluidtank",180,80,64);f.place("hbm:machine_orbus",240,80,64);
     f.place("hbm:fusion_plasma_forge",300,80,64);f.place("hbm:fusion_breeder",360,80,64);
     return "fresh complete multiblocks";'''), flush=True)
    for x, label, dx, dz, face in [(180,'tank',-1,-1,'NORTH'), (240,'orbus',1,0,'DOWN')]:
        shell=f'new BlockPos({x+dx},80,{64+dz})'
        check(label+' shell item port exists; sealed wall remains sealed', f'''
         var p=new BlockPos({x},80,64);f.reset(p,NTMFluids.DEUTERIUM,10000);
         return f.port({shell},Direction.{face})!=null && f.port(p.above(2),Direction.NORTH)==null;
        ''')
        # A vanilla hopper points horizontally into an actual outer item/fluid port.
        # Orbus has downward-facing base ports, so use its UP port at the top instead.
        hopper_shell = f'new BlockPos({x+dx},84,64)' if label=='orbus' else shell
        hopper_face = 'UP' if label=='orbus' else 'NORTH'
        hopper_direction = 'DOWN' if label=='orbus' else 'SOUTH'
        check(label+' hopper input fixture', f'''
         var q={hopper_shell}.relative(Direction.{hopper_face});
         level.setBlockAndUpdate(q,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,Direction.{hopper_direction}));
         ((net.minecraft.world.Container)level.getBlockEntity(q)).setItem(0,new ItemStack(ModItems.CELL_EMPTY.get(),2));return true;
        ''')
        eventually(label+' real ticking hopper inserts and native tank fills cells', f'''
         var m=f.machine(new BlockPos({x},80,64));
         return m.getItem(5).is(ModItems.CELL_DEUTERIUM.get()) && m.getItem(5).getCount()==2;
        ''')
        run(f'level.setBlockAndUpdate({hopper_shell}.relative(Direction.{hopper_face}),Blocks.AIR.defaultBlockState());return true;')
        check(label+' hopper extraction fixture',f'''
         var q={shell}.below();level.setBlockAndUpdate(q,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,Direction.EAST));return true;
        ''')
        eventually(label+' real ticking hopper extracts only completed cells', f'''
         var h=(net.minecraft.world.Container)level.getBlockEntity({shell}.below());
         return h.getItem(0).is(ModItems.CELL_DEUTERIUM.get()) && h.getItem(0).getCount()==2 && f.machine(new BlockPos({x},80,64)).getItem(5).isEmpty();
        ''')
        run(f'level.setBlockAndUpdate({shell}.below(),Blocks.AIR.defaultBlockState());return true;')
        # Separate powered AE grid directly against a non-core surface cell.
        bp=f'{shell}.relative(Direction.{face})'
        toward='SOUTH' if face=='NORTH' else 'UP'
        check(label+' AE grid and export fixture',f'''
         var p=new BlockPos({x},80,64);f.reset(p,NTMFluids.DEUTERIUM,10000);
         var b={bp};ae.build(b,Direction.{toward});ae.filter(b,Direction.{toward},false,AEItemKey.of(ModItems.CELL_EMPTY.get()));return true;
        ''')
        time.sleep(1.2)
        check(label+' powered AE inventory accepts exactly two empty cells',f'return ae.seed({bp},AEItemKey.of(ModItems.CELL_EMPTY.get()),2)==2;')
        eventually(label+' real AE export bus fills two cells through shell',f'''
         var m=f.machine(new BlockPos({x},80,64));return m.getItem(5).is(ModItems.CELL_DEUTERIUM.get()) && m.getItem(5).getCount()==2
          && f.tank(new BlockPos({x},80,64)).getFill()==8000 && ae.amount({bp},AEItemKey.of(ModItems.CELL_EMPTY.get()))==0;
        ''')
        run(f'ae.filter({bp},Direction.{toward},true,AEItemKey.of(ModItems.CELL_DEUTERIUM.get()));return true;')
        eventually(label+' real AE import bus returns filled cells to network',f'return ae.amount({bp},AEItemKey.of(ModItems.CELL_DEUTERIUM.get()))==2 && f.machine(new BlockPos({x},80,64)).getItem(5).isEmpty();')
        check(label+' AE fluid export fixture',f'''
         f.reset(new BlockPos({x},80,64),NTMFluids.DEUTERIUM,0);ae.filter({bp},Direction.{toward},false,AEFluidKey.of(NTMFluids.DEUTERIUM));return ae.seed({bp},AEFluidKey.of(NTMFluids.DEUTERIUM),2000)==2000;
        ''')
        eventually(label+' real AE export bus inserts fluid',f'return f.tank(new BlockPos({x},80,64)).getFill()==2000 && ae.amount({bp},AEFluidKey.of(NTMFluids.DEUTERIUM))==0;')
        run(f'''var m=f.machine(new BlockPos({x},80,64));if(m instanceof BlockEntityMachineFluidTank t){{while(t.mode!=1)t.cycleMode();}}else{{var t=(BlockEntityMachineOrbus)m;while(t.getMode()!=1)t.cycleMode();}}
         ae.filter({bp},Direction.{toward},true,AEFluidKey.of(NTMFluids.DEUTERIUM));return true;''')
        eventually(label+' real AE import bus extracts fluid without loss',f'return f.tank(new BlockPos({x},80,64)).getFill()==0 && ae.amount({bp},AEFluidKey.of(NTMFluids.DEUTERIUM))==2000;')
        # Remove grid to prevent it from touching later fixtures on reruns.
        run(f'for(int i=0;i<3;i++)level.setBlockAndUpdate({bp}.relative(Direction.{toward}.getOpposite(),i),Blocks.AIR.defaultBlockState());return true;')
    check('breeder shell accepts native recipe input, preserves output-only extraction', '''
     var p=new BlockPos(360,80,64);var m=(BlockEntityFusionBreeder)level.getBlockEntity(p);
     ItemStack input=ItemStack.EMPTY;
     for(var item:BuiltInRegistries.ITEM){var s=new ItemStack(item);if(m.canPlaceItem(1,s)){input=s;break;}}
     if(input.isEmpty())return "no breeder input";
     var q=p.offset(-1,0,0);return f.insert(q,Direction.WEST,input,false)==1 && m.getItem(1).isEmpty()
      && f.insert(q,Direction.WEST,input,true)==1 && f.extract(q,Direction.WEST,1,1,true)==0;
    ''')
    check('forge shell accepts gold-198 booster but protects blueprint and inputs', '''
     var p=new BlockPos(300,80,64);var m=(BlockEntityFusionPlasmaForge)level.getBlockEntity(p);m.clearContent();
     var s=BuiltInRegistries.ITEM.entrySet().stream().filter(e->e.getKey().identifier().toString().equals("hbm:powder_au198")).map(e->new ItemStack(e.getValue())).findFirst().orElse(ItemStack.EMPTY);
     var q=p.offset(0,0,-5);return !s.isEmpty() && f.insert(q,Direction.NORTH,s,true)==1 && m.getItem(2).getCount()==1 && f.extract(q,Direction.NORTH,2,1,true)==0;
    ''')
    for x, dx, dy, dz, face, kind in [(300,0,0,-5,'NORTH','forge'),(360,-1,0,0,'WEST','breeder')]:
        check(kind+' external fluid port preserves rollback and commits only to input tank',f'''
         var m=level.getBlockEntity(new BlockPos({x},80,64));
         var tanks=m instanceof BlockEntityFusionPlasmaForge g?g.getReceivingTanks():((BlockEntityFusionBreeder)m).tanks;
         tanks[0].setTankType(NTMFluids.DEUTERIUM);tanks[0].setFill(0);
         var h=level.getCapability(Capabilities.Fluid.BLOCK,new BlockPos({x+dx},{80+dy},{64+dz}),Direction.{face});if(h==null)return "missing fluid handler";
         try(var t=Transaction.openRoot()){{if(h.insert(FluidResource.of(NTMFluids.DEUTERIUM),1000,t)!=1000)return "simulation rejected";}}
         if(tanks[0].getFill()!=0)return "simulation mutated";
         try(var t=Transaction.openRoot()){{if(h.insert(FluidResource.of(NTMFluids.DEUTERIUM),1000,t)!=1000)return "commit rejected";t.commit();}}
         return tanks[0].getFill()==1000 && (tanks.length==1 || tanks[1].getFill()==0);
        ''')
    machine_buses()
    check('all NTM state-shaped recipes retain their actual row width in real AE matrix code', '''
     var method=Class.forName("appeng.util.CraftingRecipeUtil").getMethod("ensure3by3CraftingMatrix",net.minecraft.world.item.crafting.Recipe.class);int checked=0;
     for(var h:server.getRecipeManager().getRecipes())if(h.value() instanceof com.hbm.inventory.recipes.crafting.StateShapedRecipe){
      var r=h.value();var shape=(net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay)r.display().getFirst();
      var actual=(java.util.List<java.util.Optional<net.minecraft.world.item.crafting.Ingredient>>)method.invoke(null,r);var p=r.placementInfo();
      for(int y=0;y<3;y++)for(int x=0;x<3;x++){
       int index=x<shape.width() && y<shape.height()?p.slotsToIngredientIndex().getInt(x+y*shape.width()):-1;
       var expected=index<0?java.util.Optional.empty():java.util.Optional.of(p.ingredients().get(index));
       if(!actual.get(x+y*3).equals(expected))return h.id().identifier()+" slot "+(x+y*3);
      }checked++;
     }return checked>233;
    ''')
    (ROOT/'runs/evidence/hbm-feedback-verification.json').write_text(json.dumps({'checks':REPORT,'version':'0.1.7'},ensure_ascii=False,indent=2)+'\n')
    print('HBM_FEEDBACK_VERIFICATION_PASS', len(REPORT), flush=True)

if __name__=='__main__':
    main()
