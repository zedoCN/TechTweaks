#!/usr/bin/env python3
"""Click real JEI transfer and AE encode buttons through Minecraft Automation."""
import importlib.util,json,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('t',ROOT/'scripts/verify-hbm-feedback.py');t=importlib.util.module_from_spec(s);s.loader.exec_module(t)
v=t.a.v
REPORT=[]
def client(body):return v.rpc('unsafe.javaScratch',{'body':body,'mainThread':True})['result']
def check(name,body):
 r=t.run(body)
 if r is not True:raise AssertionError((name,r))
 REPORT.append(name);print('PASS',name,flush=True)
def transfer(recipe):
 n=client('''var rt=appeng.client.integrations.jei.JEIPlugin.instance();var rm=rt.getRecipeManager();var cat=rm.getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
 var recipes=rm.createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).get().filter(h->h.id().identifier().toString().equals("'''+recipe+''' ".trim())).toList();
 rt.getRecipesGui().showRecipes(cat,recipes,java.util.List.of());return recipes.size();''')
 assert n==1,(recipe,n)
 time.sleep(.25)
 xy=client('''var gui=appeng.client.integrations.jei.JEIPlugin.instance().getRecipesGui();var f=gui.getClass().getDeclaredField("layouts");f.setAccessible(true);var ls=f.get(gui);var ef=ls.getClass().getDeclaredField("recipeLayoutsWithButtons");ef.setAccessible(true);var entry=((java.util.List<?>)ef.get(ls)).getFirst();var bf=entry.getClass().getDeclaredField("buttons");bf.setAccessible(true);var b=(mezz.jei.gui.elements.IconButton)((java.util.List<?>)bf.get(entry)).getFirst();return (b.getX()+b.getWidth()/2)+","+(b.getY()+b.getHeight()/2);''')
 x,y=map(int,xy.split(','));r=v.rpc('screen.mouse',{'x':x,'y':y,'action':'click'});assert r['handled'],r
 time.sleep(.4)
def main():
 v.rpc('screen.close')
 t.run('''var p=new BlockPos(400,80,64);for(var q:BlockPos.betweenClosed(398,79,60,402,83,67))level.setBlockAndUpdate(q,Blocks.AIR.defaultBlockState());ae.build(p,Direction.SOUTH);ae.host(p).addPart(AEParts.PATTERN_ENCODING_TERMINAL.asItem(),Direction.SOUTH,player);player.teleportTo(400.5,80,66.5);return true;''')
 time.sleep(1)
 for recipe in ['hbm:ammo_arty_phosphorus','hbm:canister','minecraft:diamond_axe']:
  v.rpc('screen.close')
  t.run('''var p=new BlockPos(400,80,64);ae.host(p).getPart(Direction.SOUTH).onUseWithoutItem(player,new Vec3(.5,.5,1));var m=(appeng.menu.me.items.PatternEncodingTermMenu)player.containerMenu;m.clear();
   var fld=m.getClass().getDeclaredField("encodedPatternSlot");fld.setAccessible(true);((net.minecraft.world.inventory.Slot)fld.get(m)).set(ItemStack.EMPTY);return true;''')
  time.sleep(.3);transfer(recipe)
  check(recipe+' JEI click fills a valid shaped pattern on server','''var m=(appeng.menu.me.items.PatternEncodingTermMenu)player.containerMenu;var items=new java.util.ArrayList<ItemStack>();for(var slot:m.getCraftingGridSlots())items.add(slot.getItem());var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,items);
   var r=(net.minecraft.world.item.crafting.CraftingRecipe)server.getRecipeManager().getRecipes().stream().filter(h->h.id().identifier().toString().equals("'''+recipe+'''")).findFirst().get().value();return r.matches(input,level);''')
  t.run('''var m=(appeng.menu.me.items.PatternEncodingTermMenu)player.containerMenu;var fld=m.getClass().getDeclaredField("blankPatternSlot");fld.setAccessible(true);((net.minecraft.world.inventory.Slot)fld.get(m)).set(AEItems.BLANK_PATTERN.stack(1));m.broadcastChanges();return true;''')
  time.sleep(.2)
  widgets=v.rpc('screen.getState')['widgets'];button=next(w for w in widgets if w.get('visible') and w.get('message','').startswith('Encode Pattern'))
  assert v.rpc('screen.clickWidget',{'widgetPath':button['path']})['handled'];time.sleep(.3)
  check(recipe+' actual encode button produces a decodable crafting pattern','''var m=(appeng.menu.me.items.PatternEncodingTermMenu)player.containerMenu;var fld=m.getClass().getDeclaredField("encodedPatternSlot");fld.setAccessible(true);var stack=((net.minecraft.world.inventory.Slot)fld.get(m)).getItem();var decoded=appeng.api.crafting.PatternDetailsHelper.decodePattern(stack,level);return !stack.isEmpty() && decoded instanceof appeng.crafting.pattern.AECraftingPattern;''')
 v.rpc('screen.close')
 t.run('''var p=new BlockPos(420,80,64);ae.build(p,Direction.SOUTH);ae.host(p).addPart(AEParts.CRAFTING_TERMINAL.asItem(),Direction.SOUTH,player);player.teleportTo(420.5,80,66.5);return true;''')
 time.sleep(1)
 for recipe in ['hbm:ammo_arty_phosphorus','hbm:canister','minecraft:diamond_axe']:
  v.rpc('screen.close')
  t.run('''var p=new BlockPos(420,80,64);var h=server.getRecipeManager().getRecipes().stream().filter(r->r.id().identifier().toString().equals("'''+recipe+'''")).findFirst().get();
   for(var ing:h.value().placementInfo().ingredients()){var item=ing.items().findFirst().get().value();ae.seed(p,AEItemKey.of(item),9);}
   ae.host(p).getPart(Direction.SOUTH).onUseWithoutItem(player,new Vec3(.5,.5,1));return true;''')
  time.sleep(.4);transfer(recipe)
  check(recipe+' JEI click fills real AE crafting grid with matching ingredients','''var m=(appeng.menu.me.items.CraftingTermMenu)player.containerMenu;var items=new java.util.ArrayList<ItemStack>();for(int i=0;i<9;i++)items.add(m.getCraftingMatrix().getStackInSlot(i));var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,items);var r=(net.minecraft.world.item.crafting.CraftingRecipe)server.getRecipeManager().getRecipes().stream().filter(h->h.id().identifier().toString().equals("'''+recipe+'''")).findFirst().get().value();return r.matches(input,level);''')
 (ROOT/'runs/evidence/hbm-jei-verification.json').write_text(json.dumps({'checks':REPORT,'version':'0.1.7'},ensure_ascii=False,indent=2)+'\n')
 print('HBM_JEI_VERIFICATION_PASS',len(REPORT),flush=True)
if __name__=='__main__':main()
