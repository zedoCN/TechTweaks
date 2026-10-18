package cn.zedocn.tech.server.query;

import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, read-only observations, transported by the existing Minecraft command channel. */
public final class WorldQueries {
    private static final int MAX_SCAN = 8192;
    private WorldQueries() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("techquery")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(c -> send(c.getSource(), new JsonObject()))
                .then(Commands.argument("request", StringArgumentType.greedyString()).executes(c -> {
                    JsonObject parsed;
                    try {
                        String request = StringArgumentType.getString(c, "request");
                        if (request.length() > 8192) throw new IllegalArgumentException("Request exceeds 8192 characters");
                        parsed=JsonParser.parseString(request).getAsJsonObject();
                    } catch (Exception ex) { throw failure(ex); }
                    return send(c.getSource(), parsed);
                })));
    }

    private static int send(CommandSourceStack source, JsonObject request) throws CommandSyntaxException {
        try {
            JsonObject result = execute(source, request);
            String output = result.toString();
            if (output.getBytes(StandardCharsets.UTF_8).length > 48 * 1024)
                throw new IllegalArgumentException("Response exceeds 48 KiB; reduce limit or request a narrower NBT path");
            source.sendSuccess(() -> Component.literal(output), false);
            return 1;
        } catch (Exception ex) { throw failure(ex); }
    }

    private static CommandSyntaxException failure(Exception ex) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.addProperty("error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        return new SimpleCommandExceptionType(Component.literal(result.toString())).create();
    }

    public static JsonObject execute(CommandSourceStack source, JsonObject request) throws Exception {
        if (!source.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        String query = text(request, "query", "capabilities");
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.addProperty("query", query);
        out.addProperty("observed_at", java.time.Instant.now().toString());
        out.addProperty("read_only", true);
        out.addProperty("forces_chunk_loading", false);
        if (query.equals("capabilities")) {
            out.add("queries", JsonParser.parseString("[\"status\",\"map\",\"blocks.find\",\"blocks.saved\",\"blocks\",\"block\",\"entities\",\"nbt.block\",\"nbt.entity\",\"nbt.storage\",\"nbt.file\",\"nbt.chunk\",\"ae2.network\",\"ae2.items\",\"ae2.nodes\",\"ae2.interfaces\",\"ae2.patterns\",\"ae2.cpus\"]"));
            out.addProperty("ae2_available", ModList.get().isLoaded("ae2"));
            out.addProperty("max_radius", 256);
            out.addProperty("max_limit", 64);
            out.addProperty("nbt_page_characters", 6000);
            out.addProperty("usage", "techquery {\"query\":\"blocks\",\"dimension\":\"minecraft:overworld\",\"x\":0,\"y\":64,\"z\":0,\"radius\":64,\"filter\":\"ae2:\",\"offset\":0,\"limit\":32}");
            return out;
        }
        if (query.equals("status")) {
            JsonArray dimensions = new JsonArray(), players = new JsonArray();
            for (ServerLevel level : source.getServer().getAllLevels()) {
                JsonObject row = new JsonObject(); row.addProperty("dimension", level.dimension().identifier().toString());
                row.addProperty("game_time", level.getGameTime()); row.addProperty("loaded_chunks", level.getChunkSource().getLoadedChunksCount());
                row.addProperty("raining", level.isRaining()); row.addProperty("thundering", level.isThundering()); dimensions.add(row);
            }
            for (var player : source.getServer().getPlayerList().getPlayers()) {
                JsonObject row = entity(player); row.addProperty("health", player.getHealth()); players.add(row);
            }
            out.add("dimensions", dimensions); out.add("players", players); return out;
        }
        ServerLevel level = source.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                Identifier.parse(text(request, "dimension", source.getLevel().dimension().identifier().toString()))));
        if (level == null) throw new IllegalArgumentException("Unknown dimension");
        out.addProperty("dimension", level.dimension().identifier().toString());
        out.addProperty("game_time", level.getGameTime());
        BlockPos pos = new BlockPos(integer(request,"x",0,-30_000_000,30_000_000),
                integer(request,"y",64,level.getMinY(),level.getMaxY()),integer(request,"z",0,-30_000_000,30_000_000));
        int offset = integer(request,"offset",0,0,16_000_000), limit = integer(request,"limit",32,1,64);
        String filter = text(request,"filter","").toLowerCase(Locale.ROOT);
        if (filter.length() > 256) throw new IllegalArgumentException("Filter exceeds 256 characters");
        if(query.equals("map") || query.equals("blocks.find") || query.equals("blocks.saved"))
            return MapQueries.read(level,pos,request,out,query,filter,offset,limit);
        if (query.startsWith("nbt.")) {
            Tag tag;
            switch (query) {
                case "nbt.block" -> {
                    BlockEntity be = blockEntity(level, pos);
                    tag = be.saveWithFullMetadata(level.registryAccess()); out.add("position", position(pos));
                }
                case "nbt.entity" -> {
                    Entity entity = level.getEntity(UUID.fromString(text(request,"uuid","")));
                    if (entity == null) throw new IllegalArgumentException("Entity is not loaded in this dimension");
                    var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                    entity.saveWithoutId(output); tag = output.buildResult(); out.add("entity", entity(entity));
                }
                case "nbt.storage" -> {
                    var id=Identifier.parse(text(request,"id",""));
                    tag=readStorage(source.getServer().getCommandStorage(),id);
                }
                case "nbt.file", "nbt.chunk" -> tag = SavedNbt.read(level, request, out, query.equals("nbt.chunk"), pos);
                default -> throw new IllegalArgumentException("Unknown NBT query");
            }
            String path = text(request,"path","");
            if (path.length() > 1024) throw new IllegalArgumentException("NBT path exceeds 1024 characters");
            List<Tag> selected = selectPath(tag,path);
            // Each match can itself be paged; selection count is explicit rather than silently discarded.
            int match = integer(request,"match",0,0,Math.max(0,selected.size()-1));
            if (selected.isEmpty()) throw new IllegalArgumentException("NBT path has no matches");
            Tag value = selected.get(match);
            if(request.has("find")) {
                String find=text(request,"find","");
                if(find.length()>256)throw new IllegalArgumentException("NBT search exceeds 256 characters");
                NbtSearch.search(value,path,find,offset,limit,out);
                if(!out.has("source"))out.addProperty("source","live_server_nbt");
                return out;
            }
            if(value.sizeInBytes()>SavedNbt.MAX_BYTES)throw new IllegalArgumentException("Selected NBT exceeds 8 MiB; use a narrower path");
            String snbt = value.toString();
            int total=snbt.codePointCount(0,snbt.length());
            if (offset > total) throw new IllegalArgumentException("Offset exceeds NBT length");
            int next=Math.min(total,offset+integer(request,"characters",6000,1,6000));
            int start=snbt.offsetByCodePoints(0,offset),end=snbt.offsetByCodePoints(start,next-offset);
            out.addProperty("path",path); out.addProperty("match",match); out.addProperty("matches",selected.size());
            out.addProperty("nbt_type",value.getId()); out.addProperty("snbt",snbt.substring(start,end));
            out.addProperty("total_characters",total); out.addProperty("offset",offset);
            out.addProperty("truncated",next<total);
            if (next<total) out.addProperty("next_offset",next);
            if (value instanceof CompoundTag compound) {
                JsonArray keys = new JsonArray(); compound.keySet().stream().sorted().limit(256).forEach(keys::add);
                out.add("keys",keys); out.addProperty("keys_truncated",compound.size()>256);
            }
            if (!out.has("source")) out.addProperty("source","live_server_nbt");
            return out;
        }
        if (query.equals("blocks")) {
            int radius = integer(request,"radius",64,1,256), scanned=0, loaded=0, unloaded=0;
            boolean complete = true;
            List<JsonObject> rows = new ArrayList<>();
            outer: for (int cx=(pos.getX()-radius)>>4;cx<=(pos.getX()+radius)>>4;cx++) {
                for (int cz=(pos.getZ()-radius)>>4;cz<=(pos.getZ()+radius)>>4;cz++) {
                    var chunk=level.getChunkSource().getChunkNow(cx,cz);
                    if (chunk==null) {unloaded++;continue;} loaded++;
                    for (var be:chunk.getBlockEntities().values()) {
                        if (++scanned>MAX_SCAN) {complete=false;break outer;}
                        BlockPos bp=be.getBlockPos();
                        if (Math.abs((long)bp.getX()-pos.getX())>radius || Math.abs((long)bp.getZ()-pos.getZ())>radius) continue;
                        String id=BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
                        if (!matches(filter,id,be.getType().toString())) continue;
                        JsonObject row=position(bp); row.addProperty("id",id);
                        row.addProperty("block_entity",BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString()); rows.add(row);
                    }
                }
            }
            rows.sort(Comparator.comparing(JsonObject::toString));
            out.addProperty("scope","block_entities_in_loaded_chunks_horizontal_square");
            out.addProperty("scan_complete",complete); out.addProperty("scanned",Math.min(scanned,MAX_SCAN));
            out.addProperty("loaded_chunks",loaded); out.addProperty("unloaded_chunks_skipped",unloaded);
            page(out,rows,offset,limit);return out;
        }
        if (query.equals("entities")) {
            int radius=integer(request,"radius",64,1,256),scanned=0;boolean complete=true;
            List<JsonObject> rows=new ArrayList<>();
            for (Entity e:level.getAllEntities()) {
                if (++scanned>MAX_SCAN) {complete=false;break;}
                if (e.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>radius*(double)radius) continue;
                JsonObject row=entity(e);if(matches(filter,row.toString())) rows.add(row);
            }
            rows.sort(Comparator.comparing(JsonObject::toString)); out.addProperty("scan_complete",complete);
            out.addProperty("scanned",Math.min(scanned,MAX_SCAN)); page(out,rows,offset,limit); return out;
        }
        if (query.equals("block")) {
            requireLoaded(level,pos);var state=level.getBlockState(pos);
            out.add("position",position(pos));out.addProperty("id",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            JsonObject properties=new JsonObject();state.getValues().forEach(v->properties.addProperty(v.property().getName(),v.valueName()));out.add("properties",properties);
            BlockEntity be=level.getBlockEntity(pos);out.addProperty("has_block_entity",be!=null);
            List<JsonObject> items=new ArrayList<>();
            if (be instanceof Container container) {
                for(int slot=0;slot<container.getContainerSize();slot++)if(!container.getItem(slot).isEmpty()) {
                    JsonObject row=item(container.getItem(slot),level);row.addProperty("slot",slot);items.add(row);
                }
                out.addProperty("inventory_supported",true);page(out,items,offset,limit);
            } else out.addProperty("inventory_supported",false);
            return out;
        }
        if (query.startsWith("ae2.")) {
            if (!ModList.get().isLoaded("ae2")) throw new IllegalArgumentException("AE2 is not installed; generic block/NBT queries remain available");
            return Ae2Queries.read(level,pos,request,out,query,filter,offset,limit);
        }
        throw new IllegalArgumentException("Unknown query; run techquery for capabilities");
    }

    static CompoundTag readStorage(net.minecraft.world.level.storage.CommandStorage storage,Identifier id) {
        // get() performs the native read-only lazy load; keys() only sees loaded namespaces.
        CompoundTag tag=storage.get(id);
        if(storage.keys().noneMatch(id::equals))throw new IllegalArgumentException("Command storage ID does not exist");
        return tag;
    }
    static List<Tag> selectPath(Tag tag,String path) throws CommandSyntaxException {
        if(path.isEmpty())return List.of(tag);
        var reader=new StringReader(path);
        var parsed=NbtPathArgument.nbtPath().parse(reader);
        if(reader.canRead())throw new IllegalArgumentException("Unexpected trailing NBT path input");
        return parsed.get(tag);
    }
    static BlockEntity blockEntity(ServerLevel level,BlockPos pos) {
        requireLoaded(level,pos);BlockEntity be=level.getBlockEntity(pos);
        if (be==null) throw new IllegalArgumentException("No block entity at position");return be;
    }
    static void requireLoaded(ServerLevel level,BlockPos pos) {
        if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)throw new IllegalArgumentException("Chunk is not loaded; no chunk was loaded by this request");
    }
    static JsonObject position(BlockPos pos) {JsonObject j=new JsonObject();j.addProperty("x",pos.getX());j.addProperty("y",pos.getY());j.addProperty("z",pos.getZ());return j;}
    private static JsonObject entity(Entity e) {
        JsonObject j=new JsonObject();j.addProperty("uuid",e.getUUID().toString());j.addProperty("id",BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
        j.addProperty("name",e.getName().getString());j.addProperty("x",e.getX());j.addProperty("y",e.getY());j.addProperty("z",e.getZ());
        j.addProperty("dimension",e.level().dimension().identifier().toString());return j;
    }
    static JsonObject item(ItemStack stack,ServerLevel level) {
        JsonObject j=new JsonObject();j.addProperty("id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());j.addProperty("count",stack.getCount());
        j.addProperty("name",stack.getHoverName().getString());
        j.add("stack",ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow());return j;
    }
    static boolean matches(String filter,String... values) {if(filter.isEmpty())return true;for(String v:values)if(v.toLowerCase(Locale.ROOT).contains(filter))return true;return false;}
    static void page(JsonObject out,List<JsonObject> rows,int offset,int limit) {
        JsonArray data=new JsonArray();int end=(int)Math.min(rows.size(),(long)offset+limit);
        for(int i=offset;i<end;i++)data.add(rows.get(i));out.add("results",data);out.addProperty("matched",rows.size());out.addProperty("offset",offset);
        out.addProperty("truncated",end<rows.size());if(end<rows.size())out.addProperty("next_offset",end);
        out.addProperty("pagination_consistency","live_observation_repeat_query_if_world_changes");
    }
    static String text(JsonObject j,String name,String fallback) {
        if(!j.has(name))return fallback;JsonElement value=j.get(name);
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(name+" must be a string");return value.getAsString();
    }
    static int integer(JsonObject j,String name,int fallback,int min,int max) {
        if(!j.has(name))return fallback;JsonElement value=j.get(name);
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(name+" must be an integer");
        int result;try{result=value.getAsBigDecimal().intValueExact();}catch(ArithmeticException e){throw new IllegalArgumentException(name+" must be an integer");}
        if(result<min || result>max)throw new IllegalArgumentException(name+" outside "+min+".."+max);return result;
    }
}
