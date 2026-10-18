package cn.zedocn.tech.server.query;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.*;
import appeng.api.parts.*;
import appeng.api.stacks.*;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.AEBasePart;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Optional AE2 reader, loaded only after ModList confirms AE2 is present. */
final class Ae2Queries {
    private Ae2Queries() {}
    static JsonObject read(ServerLevel level,BlockPos pos,JsonObject request,JsonObject out,String query,String filter,int offset,int limit) {
        BlockEntity be=WorldQueries.blockEntity(level,pos);
        String side=WorldQueries.text(request,"side","main");
        IGridNode node=null;
        if(be instanceof IPartHost host) {
            Direction direction=side.equals("main")?null:Direction.valueOf(side.toUpperCase(Locale.ROOT));
            IPart part=host.getPart(direction);if(part!=null)node=part.getGridNode();
        } else if(be instanceof IInWorldGridNodeHost host) {
            if(!side.equals("main"))node=host.getGridNode(Direction.valueOf(side.toUpperCase(Locale.ROOT)));
            else for(Direction direction:Direction.values()) {node=host.getGridNode(direction);if(node!=null)break;}
        }
        if(node==null)throw new IllegalArgumentException("No AE2 node on requested side; use main or north/south/east/west/up/down");
        IGrid grid=node.getGrid();
        out.add("anchor",WorldQueries.position(pos));out.addProperty("side",side);
        out.addProperty("source","live_ae2_grid_loaded_members");
        out.addProperty("network_nodes",grid.size());out.addProperty("anchor_active",node.isActive());
        out.addProperty("anchor_powered",node.isPowered());out.addProperty("anchor_has_channel",node.meetsChannelRequirements());
        if(query.equals("ae2.network")) {
            var energy=grid.getEnergyService();var pathing=grid.getPathingService();
            out.addProperty("powered",energy.isNetworkPowered());out.addProperty("stored_ae",energy.getStoredPower());
            out.addProperty("capacity_ae",energy.getMaxStoredPower());out.addProperty("idle_ae_per_tick",energy.getIdlePowerUsage());
            out.addProperty("average_use_ae_per_tick",energy.getAvgPowerUsage());out.addProperty("average_input_ae_per_tick",energy.getAvgPowerInjection());
            out.addProperty("controller_state",pathing.getControllerState().toString());out.addProperty("booting",pathing.isNetworkBooting());
            out.addProperty("channel_mode",pathing.getChannelMode().toString());out.addProperty("used_channels",pathing.getUsedChannels());
            out.addProperty("storage_key_types",grid.getStorageService().getCachedInventory().size());
            out.addProperty("crafting_cpu_count",grid.getCraftingService().getCpus().size());return out;
        }
        List<JsonObject> rows=new ArrayList<>();boolean complete=true;int scanned=0;
        if(query.equals("ae2.items")) {
            if(!node.isActive())throw new IllegalArgumentException("AE2 anchor is offline; current network inventory is unavailable");
            out.addProperty("source","ae2_cached_inventory");
            for(var entry:grid.getStorageService().getCachedInventory()) {
                if(++scanned>16384){complete=false;break;}
                JsonObject row=key(entry.getKey(),entry.getLongValue(),level);
                if(WorldQueries.matches(filter,row.toString()))rows.add(row);
            }
        } else if(query.equals("ae2.cpus")) {
            for(var cpu:grid.getCraftingService().getCpus()) {
                if(++scanned>8192){complete=false;break;}
                JsonObject row=new JsonObject();row.addProperty("name",cpu.getName()==null?null:cpu.getName().getString());row.addProperty("busy",cpu.isBusy());
                row.addProperty("available_storage",cpu.getAvailableStorage());row.addProperty("coprocessors",cpu.getCoProcessors());
                row.addProperty("selection_mode",cpu.getSelectionMode().toString());
                var status=cpu.getJobStatus();
                if(status!=null) {
                    JsonObject job=new JsonObject();
                    if(status.crafting()!=null)job.add("crafting",key(status.crafting().what(),status.crafting().amount(),level));
                    job.addProperty("total_items",status.totalItems());job.addProperty("progress",status.progress());
                    job.addProperty("elapsed_time_nanoseconds",status.elapsedTimeNanos());job.addProperty("suspended",status.suspended());
                    row.add("job_status",job);
                }
                if(WorldQueries.matches(filter,row.toString()))rows.add(row);
            }
        } else if(query.equals("ae2.nodes") || query.equals("ae2.interfaces") || query.equals("ae2.patterns")) {
            for(IGridNode member:grid.getNodes()) {
                if(++scanned>8192){complete=false;break;}
                Object owner=member.getOwner();BlockEntity ownerBe=null;String ownerSide="block";
                if(owner instanceof BlockEntity block)ownerBe=block;
                else if(owner instanceof AEBasePart part){ownerBe=part.getBlockEntity();ownerSide=part.getSide()==null?"main":part.getSide().toString().toLowerCase(Locale.ROOT);}
                if(ownerBe==null || !(ownerBe.getLevel() instanceof ServerLevel ownerLevel))continue;
                if(ownerLevel.getChunkSource().getChunkNow(ownerBe.getBlockPos().getX()>>4,ownerBe.getBlockPos().getZ()>>4)==null)continue;
                JsonObject location=WorldQueries.position(ownerBe.getBlockPos());location.addProperty("dimension",ownerLevel.dimension().identifier().toString());
                location.addProperty("side",ownerSide);location.addProperty("id",owner instanceof AEBasePart part?IPartItem.getId(part.getPartItem()).toString():BuiltInRegistries.BLOCK.getKey(ownerBe.getBlockState().getBlock()).toString());
                location.addProperty("active",member.isActive());location.addProperty("powered",member.isPowered());
                location.addProperty("has_channel",member.meetsChannelRequirements());location.addProperty("used_channels",member.getUsedChannels());
                if(query.equals("ae2.nodes")) {
                    if(WorldQueries.matches(filter,location.toString()))rows.add(location);
                } else if(query.equals("ae2.interfaces") && owner instanceof InterfaceLogicHost host) {
                    location.add("stored",inventory(host.getInterfaceLogic().getStorage(),ownerLevel));
                    location.add("configured",inventory(host.getInterfaceLogic().getConfig(),ownerLevel));
                    location.addProperty("priority",host.getInterfaceLogic().getPriority());
                    if(WorldQueries.matches(filter,location.toString()))rows.add(location);
                } else if(query.equals("ae2.patterns") && owner instanceof PatternProviderLogicHost host) {
                    var inv=host.getLogic().getPatternInv();
                    for(int slot=0;slot<inv.size();slot++) {
                        var stack=inv.getStackInSlot(slot);if(stack.isEmpty())continue;
                        JsonObject row=location.deepCopy();row.addProperty("slot",slot);
                        row.addProperty("busy",host.getLogic().isBusy());row.addProperty("blocking",host.getLogic().isBlocking());
                        row.addProperty("lock_reason",host.getLogic().getCraftingLockedReason().toString());
                        var details=PatternDetailsHelper.decodePattern(stack,ownerLevel);
                        row.addProperty("decoded",details!=null);
                        if(details!=null) {
                            JsonArray outputs=new JsonArray(),inputs=new JsonArray();
                            for(var output:details.getOutputs())outputs.add(key(output.what(),output.amount(),ownerLevel));
                            for(var input:details.getInputs()) {
                                JsonObject entry=new JsonObject();entry.addProperty("multiplier",input.getMultiplier());JsonArray alternatives=new JsonArray();
                                for(var option:input.getPossibleInputs())alternatives.add(key(option.what(),option.amount(),ownerLevel));
                                entry.add("alternatives",alternatives);inputs.add(entry);
                            }
                            row.add("inputs",inputs);row.add("outputs",outputs);
                        }
                        if(WorldQueries.matches(filter,row.toString()))rows.add(row);
                        if(rows.size()>=16384){complete=false;break;}
                    }
                    if(!complete)break;
                }
            }
        } else throw new IllegalArgumentException("Unknown AE2 query");
        rows.sort(Comparator.comparing(JsonObject::toString));out.addProperty("scan_complete",complete);out.addProperty("scanned",scanned);
        WorldQueries.page(out,rows,offset,limit);return out;
    }
    private static JsonArray inventory(GenericStackInv inventory,ServerLevel level) {
        JsonArray result=new JsonArray();for(int slot=0;slot<inventory.size();slot++) {
            var stack=inventory.getStack(slot);if(stack==null)continue;
            JsonObject row=key(stack.what(),stack.amount(),level);row.addProperty("slot",slot);result.add(row);
        }return result;
    }
    private static JsonObject key(AEKey key,long amount,ServerLevel level) {
        JsonObject row=new JsonObject();row.addProperty("id",key.getId().toString());row.addProperty("name",key.getDisplayName().getString());
        row.addProperty("amount",amount);row.addProperty("amount_per_unit",key.getAmountPerUnit());row.addProperty("unit",key.getUnitSymbol());
        row.add("key",AEKey.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),key).getOrThrow());return row;
    }
}
