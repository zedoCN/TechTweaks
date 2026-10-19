package cn.zedocn.tech.server.query;

import com.google.gson.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SavedBlockContractsTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private JsonObject read(CompoundTag record)throws Exception{
        ServerLevel level=mock(ServerLevel.class);when(level.getMinY()).thenReturn(-64);when(level.getMaxY()).thenReturn(319);
        var request=JsonParser.parseString("{\"min_y\":0,\"max_y\":15}").getAsJsonObject();
        try(var saved=mockStatic(SavedNbt.class)){
            saved.when(()->SavedNbt.read(eq(level),eq(request),any(JsonObject.class),eq(true),eq(BlockPos.ZERO))).thenReturn(record);
            return MapQueries.read(level,BlockPos.ZERO,request,new JsonObject(),"blocks.saved","minecraft:stone",0,64);
        }
    }
    @Test void missingEmptyAndLegacySectionsCannotBecomeSuccessfulEmptyCoverage(){
        for(CompoundTag record:List.of(new CompoundTag(),JsonToNbt(),legacy()))
            assertTrue(assertThrows(IllegalArgumentException.class,()->read(record)).getMessage().contains("coverage unknown"));
    }
    private static CompoundTag JsonToNbt(){var tag=new CompoundTag();tag.put("sections",new ListTag());return tag;}
    private static CompoundTag legacy(){var tag=new CompoundTag();var level=new CompoundTag();level.put("Sections",new ListTag());tag.put("Level",level);return tag;}
    @Test void missingPaletteReportsUnknownWithNoFalseCompletion()throws Exception{
        var record=new CompoundTag();var sections=new ListTag();var section=new CompoundTag();section.putByte("Y",(byte)0);sections.add(section);record.put("sections",sections);
        var out=read(record);assertEquals("unknown",out.get("coverage_status").getAsString());
        assertEquals(1,out.get("unsupported_sections").getAsInt());assertFalse(out.get("scan_complete").getAsBoolean());
        assertTrue(out.get("traversal_complete").getAsBoolean());assertFalse(out.get("resume_supported").getAsBoolean());assertFalse(out.has("next_cursor"));
    }
    @Test void recognizedPaletteCanProveNoMatch()throws Exception{
        var record=new CompoundTag();var sections=new ListTag();var section=new CompoundTag();section.putByte("Y",(byte)0);
        var states=new CompoundTag();var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);
        states.put("palette",palette);section.put("block_states",states);sections.add(section);record.put("sections",sections);
        var out=read(record);assertTrue(out.get("scan_complete").getAsBoolean());assertEquals(0,out.get("returned").getAsInt());
        assertEquals(0,out.get("unsupported_sections").getAsInt());
    }
}
