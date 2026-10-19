package cn.zedocn.tech.server.query;

import com.google.gson.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorldQueryContractsTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static final class Fixture {
        final CommandSourceStack source=mock(CommandSourceStack.class);
        final MinecraftServer server=mock(MinecraftServer.class);
        final ServerLevel level=mock(ServerLevel.class);
        Fixture(){
            when(source.getServer()).thenReturn(server);when(source.getLevel()).thenReturn(level);
            when(server.isSameThread()).thenReturn(true);when(server.getLevel(any())).thenReturn(level);
            when(level.dimension()).thenReturn(Level.OVERWORLD);when(level.getMinY()).thenReturn(-64);when(level.getMaxY()).thenReturn(319);
        }
        Entity entity(){
            Entity entity=mock(Entity.class);when(entity.getUUID()).thenReturn(UUID.randomUUID());
            doReturn(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:pig"))).when(entity).getType();when(entity.getName()).thenReturn(Component.literal("fixture"));
            when(entity.level()).thenReturn(level);return entity;
        }
        CompoundTag storage(){
            var root=new CompoundTag();var storage=mock(CommandStorage.class);
            when(server.getCommandStorage()).thenReturn(storage);when(storage.get(any())).thenReturn(root);
            when(storage.keys()).thenAnswer(call->java.util.stream.Stream.of(Identifier.parse("test:stored")));
            return root;
        }
        JsonObject read(String json)throws Exception{return WorldQueries.execute(source,JsonParser.parseString(json).getAsJsonObject());}
    }
    @Test void nearestBlockEntityIsNotHiddenByLexicographicPrefixAndDistanceTiesAreStable()throws Exception{
        var f=new Fixture();var cache=mock(net.minecraft.server.level.ServerChunkCache.class);
        when(f.level.getChunkSource()).thenReturn(cache);
        var chunks=new HashMap<String,net.minecraft.world.level.chunk.LevelChunk>();
        var entries=new LinkedHashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity>();
        for(int x=-590;x<=-568;x++)for(int z=3110;z<=3112;z++)
            entries.put(new net.minecraft.core.BlockPos(x,50,z),null);
        var nearest=new net.minecraft.core.BlockPos(-591,110,3111);entries.put(nearest,null);
        entries.put(new net.minecraft.core.BlockPos(-591,114,3111),null);
        var type=net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.parse("minecraft:chest"));
        var byChunk=new HashMap<String,Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity>>();
        for(var position:entries.keySet()){
            var be=mock(net.minecraft.world.level.block.entity.BlockEntity.class);
            when(be.getBlockPos()).thenReturn(position);doReturn(type).when(be).getType();
            when(be.getBlockState()).thenReturn(net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            byChunk.computeIfAbsent((position.getX()>>4)+","+(position.getZ()>>4),key->new LinkedHashMap<>()).put(position,be);
        }
        for(var chunk:byChunk.entrySet()){
            var loaded=mock(net.minecraft.world.level.chunk.LevelChunk.class);when(loaded.getBlockEntities()).thenReturn(chunk.getValue());
            chunks.put(chunk.getKey(),loaded);
        }
        when(cache.getChunkNow(anyInt(),anyInt())).thenAnswer(call->chunks.get(call.getArgument(0)+","+call.getArgument(1)));
        String request="{\"query\":\"blocks\",\"x\":-591,\"y\":112,\"z\":3111,\"radius\":24,\"limit\":1";
        var first=f.read(request+"}");var row=first.getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals(-591,row.get("x").getAsInt());assertEquals(110,row.get("y").getAsInt());
        assertEquals(4.0,row.get("distance_squared").getAsDouble());assertEquals(71,first.get("matched").getAsInt());
        assertEquals("distance_squared_ascending_then_row_json",first.get("order").getAsString());
        assertTrue(first.get("truncated").getAsBoolean());assertEquals(1,first.get("next_offset").getAsInt());
        var second=f.read(request+",\"offset\":1}").getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals(114,second.get("y").getAsInt());assertEquals(4.0,second.get("distance_squared").getAsDouble());
    }
    @Test void entitySearchUsesLoadedSpatialCandidatesInsteadOfGlobalPrefix()throws Exception{
        var f=new Fixture();Entity target=f.entity();
        doAnswer(call->{
            AABB box=call.getArgument(1);assertEquals(96.5,box.minX);assertEquals(104.5,box.maxX);
            java.util.function.Predicate<Entity> predicate=call.getArgument(2);
            Entity spectator=mock(Entity.class);when(spectator.isSpectator()).thenReturn(true);
            assertTrue(predicate.test(spectator),"spectators must not be silently excluded by the spatial API default");
            List<Entity> candidates=call.getArgument(3);candidates.add(target);
            assertEquals(8193,call.<Integer>getArgument(4));return null;
        }).when(f.level).getEntities(any(net.minecraft.world.level.entity.EntityTypeTest.class),any(AABB.class),any(),anyList(),eq(8193));
        when(f.level.getAllEntities()).thenThrow(new AssertionError("global prefix must not be visited"));
        var out=f.read("{\"query\":\"entities\",\"x\":100,\"y\":70,\"z\":-100,\"radius\":4}");
        assertEquals(1,out.get("matched").getAsInt());assertEquals(1,out.get("scanned").getAsInt());
        assertTrue(out.get("scan_complete").getAsBoolean());verify(f.level,never()).getAllEntities();
    }
    @Test void localEntityBudgetIsExplicitlyNonResumable()throws Exception{
        var f=new Fixture();var entity=f.entity();
        doAnswer(call->{List<Entity> candidates=call.getArgument(3);candidates.addAll(Collections.nCopies(8193,entity));return null;})
                .when(f.level).getEntities(any(net.minecraft.world.level.entity.EntityTypeTest.class),any(AABB.class),any(),anyList(),eq(8193));
        var out=f.read("{\"query\":\"entities\",\"x\":0,\"y\":64,\"z\":0,\"filter\":\"unmatched\"}");
        assertFalse(out.get("scan_complete").getAsBoolean());assertEquals(8192,out.get("scanned").getAsInt());
        assertFalse(out.get("resume_supported").getAsBoolean());assertEquals("partial",out.get("coverage_status").getAsString());
        assertFalse(out.has("next_offset"));assertTrue(out.get("resume_reason").getAsString().contains("filter"));
    }
    @Test void storageIgnoresLegacyCoordinatesAndDimensionAndPreservesSelectedFindIdentity()throws Exception{
        var f=new Fixture();var root=f.storage();var items=new ListTag();
        for(String id:new String[]{"alpha","beta"}){var item=new CompoundTag();item.putString("id",id);items.add(item);}root.put("Items",items);
        when(f.level.getMinY()).thenThrow(new AssertionError("storage is not a spatial query"));
        var out=f.read("{\"query\":\"nbt.storage\",\"id\":\"test:stored\",\"dimension\":\"test:ignored\",\"y\":-99999,\"path\":\"Items[]\",\"match\":1,\"find\":\"beta\"}");
        assertEquals("server_command_storage",out.get("scope").getAsString());assertFalse(out.has("dimension"));
        assertEquals("Items[]",out.get("selected_path").getAsString());assertEquals(1,out.get("selected_match").getAsInt());
        assertEquals(2,out.get("selected_matches").getAsInt());
        var row=out.getAsJsonArray("results").get(0).getAsJsonObject();
        assertEquals("\"id\"",row.get("relative_path").getAsString());assertEquals("selected_value_relative",row.get("path_scope").getAsString());
        assertEquals("\"beta\"",row.get("preview").getAsString());verify(f.server,never()).getLevel(any());
    }
    @Test void entityNbtNeedsIdentityAndDimensionButNoHeightOrCoordinates()throws Exception{
        var f=new Fixture();Entity entity=f.entity();when(f.level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        when(f.level.getEntity(entity.getUUID())).thenReturn(entity);
        when(f.level.getMinY()).thenThrow(new AssertionError("entity NBT is not a positional query"));
        var out=f.read("{\"query\":\"nbt.entity\",\"dimension\":\"minecraft:overworld\",\"uuid\":\""+entity.getUUID()+"\",\"y\":-99999}");
        assertEquals("{}",out.get("snbt").getAsString());assertEquals(entity.getUUID().toString(),out.getAsJsonObject("entity").get("uuid").getAsString());
    }
    @Test void nbtFindBudgetDoesNotPretendResultOffsetResumesTraversal(){
        var root=new CompoundTag();var list=new ListTag();for(int i=0;i<8200;i++)list.add(IntTag.valueOf(i));root.put("list",list);
        var out=new JsonObject();NbtSearch.search(root,"Items[]","never matches",0,64,out);
        assertFalse(out.get("scan_complete").getAsBoolean());assertFalse(out.get("resume_supported").getAsBoolean());
        assertEquals(8192,out.get("scanned").getAsInt());assertTrue(out.has("scan_incomplete_reason"));assertFalse(out.has("next_offset"));
    }
}
