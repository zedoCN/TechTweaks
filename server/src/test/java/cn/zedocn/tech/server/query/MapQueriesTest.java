package cn.zedocn.tech.server.query;

import com.google.gson.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapQueriesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static final class Fixture {
        final ServerLevel level=mock(ServerLevel.class);
        final ServerChunkCache cache=mock(ServerChunkCache.class);
        final Map<String,LevelChunk> chunks=new HashMap<>();
        final Map<String,LevelChunkSection[]> sections=new HashMap<>();
        Fixture() {
            when(level.getMinY()).thenReturn(-64);when(level.getMaxY()).thenReturn(319);
            when(level.getChunkSource()).thenReturn(cache);
            when(cache.getChunkNow(anyInt(),anyInt())).thenAnswer(call->chunks.get(call.getArgument(0)+","+call.getArgument(1)));
        }
        void load(int cx,int cz,BlockState fill) {
            var sectionArray=new LevelChunkSection[24];
            for(int i=0;i<sectionArray.length;i++) {
                sectionArray[i]=new LevelChunkSection(new PalettedContainer<>(fill,
                        Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY)),null);
            }
            LevelChunk chunk=mock(LevelChunk.class);
            when(chunk.getSectionIndex(anyInt())).thenAnswer(call->(call.<Integer>getArgument(0)+64)>>4);
            when(chunk.getSection(anyInt())).thenAnswer(call->sectionArray[call.<Integer>getArgument(0)]);
            chunks.put(cx+","+cz,chunk);sections.put(cx+","+cz,sectionArray);
        }
        void set(BlockPos p,BlockState state) {
            sections.get((p.getX()>>4)+","+(p.getZ()>>4))[(p.getY()+64)>>4]
                    .setBlockState(p.getX()&15,p.getY()&15,p.getZ()&15,state);
        }
        JsonObject read(BlockPos center,JsonObject request,int limit) throws Exception {
            return MapQueries.read(level,center,request,new JsonObject(),"blocks.find","minecraft:stone",0,limit);
        }
    }
    private static JsonObject request(int radius,int minY,int maxY,int budget) {
        var request=new JsonObject();request.addProperty("radius",radius);request.addProperty("min_y",minY);
        request.addProperty("max_y",maxY);request.addProperty("scan_budget",budget);return request;
    }
    private static String position(JsonElement row) {
        var object=row.getAsJsonObject();return object.get("x")+","+object.get("y")+","+object.get("z");
    }

    @Test void sparseRadius256ReachesAnchorOnFirstPageAndSkipsWholeUnloadedChunks() throws Exception {
        var fixture=new Fixture();var center=new BlockPos(-5000,111,714);
        fixture.load(center.getX()>>4,center.getZ()>>4,Blocks.AIR.defaultBlockState());
        fixture.set(center,Blocks.STONE.defaultBlockState());
        for(int radius:new int[]{64,256}) {
            clearInvocations(fixture.cache);
            var result=fixture.read(center,request(radius,-64,319,32768),64);
            assertEquals(1,result.get("returned").getAsInt());assertEquals("-5000,111,714",position(result.getAsJsonArray("results").get(0)));
            assertTrue(result.get("scan_complete").getAsBoolean());assertEquals(4096,result.get("scanned_blocks").getAsInt());
            assertTrue(result.get("unloaded_chunks_skipped").getAsInt()>(radius==256?1000:50));
            assertTrue(result.get("palette_sections_skipped").getAsInt()>=23);
            assertTrue(result.get("work_units").getAsInt()<=32768);
            verify(fixture.cache,atMost(1089)).getChunkNow(anyInt(),anyInt());
            verifyNoMoreInteractions(fixture.cache);
        }
    }

    @Test void paginationCoversClippedNegativeChunksAndSectionsExactlyOnce() throws Exception {
        var fixture=new Fixture();var center=new BlockPos(-16,-16,-16);
        for(int cx=-2;cx<=-1;cx++)for(int cz=-2;cz<=-1;cz++)fixture.load(cx,cz,Blocks.STONE.defaultBlockState());
        var request=request(2,-17,-13,11);var seen=new HashSet<String>();int pages=0;
        while(true) {
            var result=fixture.read(center,request,7);pages++;
            assertTrue(result.get("work_units").getAsInt()<=11);
            for(var row:result.getAsJsonArray("results"))assertTrue(seen.add(position(row)),"duplicate "+row);
            if(result.get("scan_complete").getAsBoolean())break;
            int next=result.get("next_cursor").getAsInt();assertTrue(next<0);request.addProperty("cursor",next);
            assertTrue(pages<100);
        }
        var expected=new HashSet<String>();
        for(int x=-18;x<=-14;x++)for(int y=-17;y<=-13;y++)for(int z=-18;z<=-14;z++)expected.add(x+","+y+","+z);
        assertEquals(expected,seen);assertTrue(pages>1);
    }

    @Test void budgetOneAlwaysMakesProgressIncludingSkippedRegions() throws Exception {
        var fixture=new Fixture();fixture.load(0,0,Blocks.AIR.defaultBlockState());
        fixture.set(new BlockPos(0,0,0),Blocks.STONE.defaultBlockState());
        var request=request(1,-1,1,1);int previous=0,pages=0,returned=0;
        while(true) {
            var result=fixture.read(BlockPos.ZERO,request,1);pages++;returned+=result.get("returned").getAsInt();
            assertEquals(1,result.get("work_units").getAsInt());assertTrue(result.get("visited_positions").getAsInt()>0);
            if(result.get("scan_complete").getAsBoolean())break;
            int next=-result.get("next_cursor").getAsInt()-1;assertTrue(next>previous);previous=next;
            request.add("cursor",result.get("next_cursor"));assertTrue(pages<30);
        }
        assertEquals(1,returned);
    }

    @Test void clipsInclusiveYAtWorldTopAndBottom() throws Exception {
        var fixture=new Fixture();fixture.load(0,0,Blocks.STONE.defaultBlockState());
        for(int y:new int[]{-64,319}) {
            var result=fixture.read(new BlockPos(1,y,1),request(1,y,y,32768),64);
            assertEquals(9,result.get("returned").getAsInt());assertEquals(9,result.get("total_positions").getAsInt());
            for(var row:result.getAsJsonArray("results"))assertEquals(y,row.getAsJsonObject().get("y").getAsInt());
        }
    }

    @Test void explicitMinimumAboveDefaultUpperBoundStillProducesValidClippedRange() throws Exception {
        var fixture=new Fixture();fixture.load(0,0,Blocks.STONE.defaultBlockState());
        var request=new JsonObject();request.addProperty("radius",1);request.addProperty("min_y",319);
        var result=fixture.read(new BlockPos(1,64,1),request,64);
        assertEquals(319,result.get("max_y").getAsInt());assertEquals(9,result.get("returned").getAsInt());
    }

    @Test void propertyOnlyFilterUsesPaletteAndExactStateChecks() throws Exception {
        var fixture=new Fixture();fixture.load(0,0,Blocks.AIR.defaultBlockState());
        var target=new BlockPos(1,1,1);
        fixture.set(target,Blocks.OAK_LOG.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,net.minecraft.core.Direction.Axis.X));
        fixture.set(new BlockPos(2,1,1),Blocks.OAK_LOG.defaultBlockState());
        var request=request(1,0,2,32768);var properties=new JsonObject();properties.addProperty("axis","x");request.add("properties",properties);
        var result=MapQueries.read(fixture.level,target,request,new JsonObject(),"blocks.find","",0,64);
        assertEquals(1,result.get("returned").getAsInt());assertEquals("1,1,1",position(result.getAsJsonArray("results").get(0)));
    }

    @Test void positivePublishedContinuationRetainsLegacyColumnOrder() throws Exception {
        var fixture=new Fixture();
        for(int cx=-1;cx<=0;cx++)for(int cz=-1;cz<=0;cz++)fixture.load(cx,cz,Blocks.STONE.defaultBlockState());
        var request=request(1,0,1,1);request.addProperty("cursor",1);
        var result=fixture.read(BlockPos.ZERO,request,1);
        assertEquals("legacy_columns_v1",result.get("scan_order").getAsString());
        assertEquals("-1,1,-1",position(result.getAsJsonArray("results").get(0)));
        assertEquals(2,result.get("next_cursor").getAsInt());
    }

    @Test void cursorBoundsAndAlreadyCompleteTokenNeverAccessChunks() throws Exception {
        var fixture=new Fixture();var request=request(1,0,0,1);
        request.addProperty("cursor",-10);
        var result=fixture.read(BlockPos.ZERO,request,1);assertTrue(result.get("scan_complete").getAsBoolean());
        assertFalse(result.has("next_cursor"));assertEquals(0,result.get("work_units").getAsInt());
        verifyNoInteractions(fixture.cache);
        for(int bad:new int[]{-11,10,Integer.MIN_VALUE,Integer.MAX_VALUE}) {
            request.addProperty("cursor",bad);assertThrows(IllegalArgumentException.class,()->fixture.read(BlockPos.ZERO,request,1));
        }
    }
}
