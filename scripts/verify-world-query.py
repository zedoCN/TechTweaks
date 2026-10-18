#!/usr/bin/env python3
"""Exercise read-only world/AE2 queries against the isolated GuidanceVerification client."""
import importlib.util,json,pathlib,time
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('guidance',ROOT/'scripts/verify-guidance.py');v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
REPORT=[];TIMINGS=[]
def check(name,value):
 if not value:raise AssertionError(name)
 REPORT.append(name);print('PASS '+name,flush=True)
def query(name,allow_failure=False,permission=True,**params):
 req=json.dumps({'query':name,'x':33,'y':80,'z':0,**params},separators=(',',':'))
 cmd='techquery '+req
 body='''class Sink implements net.minecraft.commands.CommandSource {StringBuilder output=new StringBuilder();public void sendSystemMessage(net.minecraft.network.chat.Component m){output.append(m.getString());}public boolean acceptsSuccess(){return true;}public boolean acceptsFailure(){return true;}public boolean shouldInformAdmins(){return false;}}
 var sink=new Sink();var success=new boolean[]{false};var source=server.createCommandSourceStack().withSource(sink).withCallback((ok,value)->success[0]=ok);
 '''+('' if permission else 'source=source.withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS);')+'''
 long start=System.nanoTime();server.getCommands().performPrefixedCommand(source,'''+json.dumps(cmd)+''');var result=new com.google.gson.JsonObject();result.addProperty("success",success[0]);result.addProperty("output",sink.output.toString());result.addProperty("elapsed_ms",(System.nanoTime()-start)/1000000.0);return result.toString();'''
 result=json.loads(v.server(body));TIMINGS.append({'query':name,'ms':result['elapsed_ms']})
 if not result['success']:
  if not allow_failure:raise AssertionError((name,result))
  return result
 if allow_failure:raise AssertionError(('unexpected success',name,result))
 return json.loads(result['output'])

def main():
 v.rpc('unsafe.javaScratch',{'body':'((net.minecraft.client.Minecraft)ctx.minecraft()).options.pauseOnLostFocus=false;return true;','mainThread':True});v.rpc('screen.close')
 print(v.server('''player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.teleportTo(32.5,80,3.5);
 for(var p:BlockPos.betweenClosed(30,79,-2,40,79,4))level.setBlockAndUpdate(p,Blocks.BEDROCK.defaultBlockState());
 for(var p:BlockPos.betweenClosed(30,80,-2,40,83,4))level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
 level.setBlockAndUpdate(new BlockPos(32,80,0),appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
 level.setBlockAndUpdate(new BlockPos(33,80,0),appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
 level.setBlockAndUpdate(new BlockPos(34,80,0),appeng.core.definitions.AEBlocks.INTERFACE.block().defaultBlockState());
 level.setBlockAndUpdate(new BlockPos(35,80,0),appeng.core.definitions.AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
 level.setBlockAndUpdate(new BlockPos(36,80,0),appeng.core.definitions.AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
 var drive=(appeng.blockentity.storage.DriveBlockEntity)level.getBlockEntity(new BlockPos(33,80,0));
 drive.getInternalInventory().setItemDirect(0,appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());drive.getInternalInventory().setItemDirect(1,appeng.core.definitions.AEItems.FLUID_CELL_1K.stack());
 var provider=(appeng.helpers.patternprovider.PatternProviderLogicHost)level.getBlockEntity(new BlockPos(35,80,0));
 var pattern=appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND),2)),List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.EMERALD),1)));
 provider.getLogic().getPatternInv().setItemDirect(0,pattern);
 var cp=new BlockPos(33,80,1);level.setBlockAndUpdate(cp,appeng.core.definitions.AEBlocks.CABLE_BUS.block().defaultBlockState());var host=(appeng.api.parts.IPartHost)level.getBlockEntity(cp);
 host.addPart(appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT),null,player);host.addPart(appeng.core.definitions.AEParts.INTERFACE.asItem(),Direction.SOUTH,player);host.addPart(appeng.core.definitions.AEParts.PATTERN_PROVIDER.asItem(),Direction.EAST,player);
 ((appeng.helpers.patternprovider.PatternProviderLogicHost)host.getPart(Direction.EAST)).getLogic().getPatternInv().setItemDirect(2,pattern.copy());
 var chestPos=new BlockPos(38,80,0);level.setBlockAndUpdate(chestPos,Blocks.BARREL.defaultBlockState());var chest=(net.minecraft.world.Container)level.getBlockEntity(chestPos);var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7);item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("query_marker😀"));chest.setItem(0,item);
 var tag=new net.minecraft.nbt.CompoundTag();tag.putLong("value",Long.MAX_VALUE);server.getCommandStorage().set(net.minecraft.resources.Identifier.parse("techquery:fixture"),tag);
 return "query fixture created";'''))
 time.sleep(2)
 check('capabilities discover 18 read-only data queries',len(query('capabilities')['queries'])==18)
 check('non-admin command source is denied',not query('status',allow_failure=True,permission=False)['success'])
 for bad in [{'radius':257},{'radius':1.5},{'limit':65},{'limit':'10'}]:query('blocks',allow_failure=True,**bad)
 check('invalid ranges and numeric types fail',True)
 status=query('status');check('server status reports dimensions and real player UUID',status['dimensions'] and len(status['players'][0]['uuid'])==36)
 blocks=query('blocks',radius=16,filter='ae2:',limit=1);check('loaded machine scan paginates and identifies skipped chunks',blocks['matched']>=6 and blocks['truncated'] and blocks['next_offset']==1)
 block=query('block',x=38);check('container query preserves slot count and item components',block['results'][0]['count']==7 and 'query_marker' in json.dumps(block['results'][0]))
 nbt=query('nbt.block',x=38,find='query_marker');check('NBT search returns exact paths',nbt['scan_complete'] and nbt['matched']>=1 and 'components' in nbt['results'][0]['path'])
 root=query('nbt.block',x=38,characters=1);parts=root['snbt'];offset=root.get('next_offset')
 while offset is not None:
  part=query('nbt.block',x=38,characters=13,offset=offset);parts+=part['snbt'];offset=part.get('next_offset')
 whole=query('nbt.block',x=38);check('Unicode-safe NBT pages reconstruct exact full SNBT',parts==whole['snbt'] and '😀' in parts)
 path=nbt['results'][0]['path'];check('returned NBT search path can be read', 'query_marker' in query('nbt.block',x=38,path=path)['snbt'])
 storage=query('nbt.storage',id='techquery:fixture',path='value');check('NBT long precision is preserved',storage['snbt']=='9223372036854775807L')
 query('nbt.storage',id='techquery:absent',allow_failure=True);check('missing command storage reports unknown rather than empty',True)
 entity=query('nbt.entity',uuid=status['players'][0]['uuid'],path='Pos');check('live entity NBT is readable',entity['matches']==1)
 entities=query('entities',radius=32,filter='TechPortTest');check('entity scan returns UUID and position',entities['matched']==1)
 before=v.server('return level.getChunkSource().getLoadedChunksCount();');query('block',x=100000,z=100000,allow_failure=True);query('blocks',x=100000,z=100000,radius=16);after=v.server('return level.getChunkSource().getLoadedChunksCount();');check('unloaded queries do not load chunks',before==after)
 v.server('server.saveEverything(true,true,true);return true;')
 file=query('nbt.file',file='level.dat');check('saved dat carries timestamp hash and consistency boundary',file['source']=='saved_world_file' and len(file['record_sha256'])==64 and file['file_modified_at'])
 chunk=query('nbt.chunk',find='ae2:');check('native saved Anvil chunk NBT is searchable',chunk['matched']>0 and chunk['source']=='saved_world_file' and chunk['chunk_x']==2)
 query('nbt.file',file='../server.properties',allow_failure=True);query('nbt.file',file='../../outside.dat',allow_failure=True);check('saved file access cannot escape world root',True)
 surface=query('map',radius=16,step=16,limit=9);check('map samples surface elevation block and biome',surface['total_samples']==9 and any(r['loaded'] and 'biome' in r and 'surface_y' in r for r in surface['results']))
 v.server('level.setBlockAndUpdate(new BlockPos(39,81,2),Blocks.DIAMOND_BLOCK.defaultBlockState());level.setBlockAndUpdate(new BlockPos(40,81,2),Blocks.DIAMOND_BLOCK.defaultBlockState());level.setBlockAndUpdate(new BlockPos(39,82,2),Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));server.saveEverything(true,true,true);return true;')
 found=[];cursor=0;rounds=0
 while True:
  batch=query('blocks.find',x=39,y=81,z=2,radius=2,min_y=80,max_y=83,filter='minecraft:diamond_block',scan_budget=7,limit=1,cursor=cursor);found+=batch['results'];rounds+=1
  if batch['scan_complete']:break
  assert batch['next_cursor']>cursor;cursor=batch['next_cursor'];assert rounds<100
 check('ordinary block brute search resumes without missing or duplicating coordinates',len(found)==2 and {(r['x'],r['y'],r['z']) for r in found}=={(39,81,2),(40,81,2)})
 state=query('blocks.find',x=39,y=82,z=2,radius=1,min_y=82,max_y=82,filter='oak_log',properties={'axis':'x'});check('ordinary block search matches exact state properties',len(state['results'])==1 and state['results'][0]['properties']['axis']=='x')
 saved=[];cursor=0
 while True:
  batch=query('blocks.saved',filter='minecraft:diamond_block',min_y=80,max_y=83,scan_budget=4096,limit=1,cursor=cursor);saved+=batch['results']
  if batch['scan_complete']:break
  assert batch['next_cursor']>cursor;cursor=batch['next_cursor']
 check('saved map palettes resolve ordinary block world coordinates',len(saved)==2 and {(r['x'],r['y'],r['z']) for r in saved}=={(39,81,2),(40,81,2)})
 network=query('ae2.network');check('real AE2 grid power channels and CPU are readable',network['powered'] and network['used_channels']>=4 and network['crafting_cpu_count']==1)
 print(v.server('''var be=(appeng.blockentity.storage.DriveBlockEntity)level.getBlockEntity(new BlockPos(33,80,0));var inv=be.getGridNode(Direction.UP).getGrid().getStorageService().getInventory();var source=appeng.api.networking.security.IActionSource.ofPlayer(player);inv.insert(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND),64,appeng.api.config.Actionable.MODULATE,source);inv.insert(appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER),2000,appeng.api.config.Actionable.MODULATE,source);var named=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("variant"));inv.insert(appeng.api.stacks.AEItemKey.of(named),9,appeng.api.config.Actionable.MODULATE,source);var iface=(appeng.helpers.InterfaceLogicHost)level.getBlockEntity(new BlockPos(34,80,0));var gold=new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT),7);iface.getInterfaceLogic().getConfig().setStack(0,gold);iface.getInterfaceLogic().getStorage().setStack(0,gold);return "inventory seeded";'''))
 time.sleep(1)
 items=query('ae2.items',filter='diamond');check('network items preserve component variants and quantities',sorted(row['amount'] for row in items['results'])==[9,64])
 water=query('ae2.items',filter='minecraft:water');check('network fluids report conversion units',water['results'][0]['amount']==2000 and water['results'][0]['amount_per_unit']==1000)
 interfaces=query('ae2.interfaces',filter='gold_ingot');check('interfaces return configured and stocked items at coordinate',interfaces['matched']==1 and interfaces['results'][0]['x']==34 and interfaces['results'][0]['stored'][0]['amount']==7)
 nodes=query('ae2.nodes',x=33,z=1);check('cable anchor resolves block and multipart node locations',any(row['side']=='south' and row['x']==33 and row['z']==1 for row in nodes['results']))
 attached=query('ae2.interfaces',x=33,z=1,side='south');check('multipart interface can anchor its own network',attached['matched']==2)
 patterns=query('ae2.patterns',filter='emerald');check('pattern search returns block and multipart coordinates and slots',patterns['matched']==2 and {(r['x'],r['z'],r['side'],r['slot']) for r in patterns['results']}=={(35,0,'block',0),(33,1,'east',2)})
 check('decoded patterns preserve inputs multipliers and outputs',all(r['inputs'][0]['multiplier']==2 and r['outputs'][0]['id']=='minecraft:emerald' for r in patterns['results']))
 cpus=query('ae2.cpus');check('real crafting CPU status is readable',cpus['matched']==1 and not cpus['results'][0]['busy'] and cpus['results'][0]['available_storage']==1024)
 again=query('ae2.items',filter='diamond');check('query suite never extracts or inserts items',sorted(r['amount'] for r in again['results'])==[9,64])
 (ROOT/'runs/evidence/world-query-verification.json').write_text(json.dumps({'checks':REPORT,'timings':TIMINGS},ensure_ascii=False,indent=2)+'\n')
 print('WORLD_QUERY_VERIFICATION_PASS',len(REPORT))
if __name__=='__main__':main()
