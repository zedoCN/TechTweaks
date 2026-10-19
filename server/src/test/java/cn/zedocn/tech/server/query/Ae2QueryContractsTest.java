package cn.zedocn.tech.server.query;

import appeng.api.networking.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Ae2QueryContractsTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private static final class Fixture {
        final ServerLevel level=mock(ServerLevel.class);final ServerChunkCache cache=mock(ServerChunkCache.class);
        final IGrid grid=mock(IGrid.class);final IGridNode anchorNode=mock(IGridNode.class);
        Fixture(){
            when(level.getChunkSource()).thenReturn(cache);when(cache.getChunkNow(anyInt(),anyInt())).thenReturn(mock(LevelChunk.class));
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            BlockEntity anchor=mock(BlockEntity.class,withSettings().extraInterfaces(IInWorldGridNodeHost.class));
            when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(anchor);
            when(((IInWorldGridNodeHost)anchor).getGridNode(any(Direction.class))).thenReturn(anchorNode);
            when(anchorNode.getGrid()).thenReturn(grid);
        }
        IGridNode owner(Object owner){var node=mock(IGridNode.class);when(node.getOwner()).thenReturn(owner);return node;}
        BlockEntity block(ServerLevel ownerLevel,int x){var be=mock(BlockEntity.class);when(be.getLevel()).thenReturn(ownerLevel);
            when(be.getBlockPos()).thenReturn(new BlockPos(x,0,0));when(be.getBlockState()).thenReturn(Blocks.STONE.defaultBlockState());return be;}
        JsonObject read(){return Ae2Queries.read(level,BlockPos.ZERO,new JsonObject(),new JsonObject(),"ae2.nodes","",0,64);}
    }
    @Test void preservesCrossDimensionMembersAndReportsAllUnlocatableCategories(){
        var f=new Fixture();var remote=mock(ServerLevel.class);var remoteCache=mock(ServerChunkCache.class);
        when(remote.dimension()).thenReturn(Level.NETHER);when(remote.getChunkSource()).thenReturn(remoteCache);
        when(remoteCache.getChunkNow(eq(1),eq(0))).thenReturn(mock(LevelChunk.class));
        var nodes=List.of(f.owner(new Object()),f.owner(f.block(null,0)),f.owner(f.block(remote,0)),f.owner(f.block(f.level,0)),f.owner(f.block(remote,16)));
        when(f.grid.getNodes()).thenReturn(nodes);when(f.grid.size()).thenReturn(nodes.size());
        var out=f.read();assertTrue(out.get("scan_complete").getAsBoolean());assertFalse(out.get("node_coverage_complete").getAsBoolean());
        assertEquals("partial",out.get("coverage_status").getAsString());assertEquals(1,out.get("unsupported_owner_nodes_skipped").getAsInt());
        assertEquals(1,out.get("unavailable_level_nodes_skipped").getAsInt());assertEquals(1,out.get("unloaded_nodes_skipped").getAsInt());
        assertEquals(2,out.get("located_nodes").getAsInt());assertEquals(2,out.getAsJsonArray("results").size());
        assertTrue(out.getAsJsonArray("results").toString().contains("minecraft:the_nether"));
        assertFalse(out.get("resume_supported").getAsBoolean());
    }
    @Test void budgetCountsVisitedNodesWithoutClaimingTailResume(){
        var f=new Fixture();var node=f.owner(new Object());when(f.grid.getNodes()).thenReturn(Collections.nCopies(8193,node));
        var out=f.read();assertFalse(out.get("scan_complete").getAsBoolean());assertEquals(8192,out.get("scanned").getAsInt());
        assertEquals(8192,out.get("unsupported_owner_nodes_skipped").getAsInt());assertFalse(out.get("resume_supported").getAsBoolean());
        assertTrue(out.has("scan_incomplete_reason"));
    }
}
