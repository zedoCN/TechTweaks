package cn.zedocn.tech;

import com.hbm.api.energymk2.PowerGraph;
import com.hbm.api.fluidmk2.FluidPipeGraph;
import com.hbm.blockentity.network.BlockEntityPipeAnchor;
import com.hbm.blockentity.network.BlockEntityPylonBase;
import com.hbm.blocks.multiblock.MultiblockSurface;
import com.hbm.uninos.graph.LevelNodeGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.Consumer;

/** Select the two existing endpoints, just like HBM's wiring spool and pipe wrench. */
public final class VirtualLinkCutterItem extends Item {
    private static final String START = "tech_link_start";
    private static final String DIMENSION = "tech_link_dimension";
    private static final String KIND = "tech_link_kind";

    public VirtualLinkCutterItem(Properties properties) { super(properties.stacksTo(1)); }

    private static CompoundTag selection(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void clear(ItemStack stack, CompoundTag tag) {
        tag.remove(START); tag.remove(DIMENSION); tag.remove(KIND);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static BlockEntity endpoint(Level level, BlockPos clicked) {
        if (!level.hasChunkAt(clicked)) return null;
        BlockPos core = MultiblockSurface.coreOfAny(level, clicked);
        BlockPos pos = core == null ? clicked : core;
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof BlockEntityPylonBase || entity instanceof BlockEntityPipeAnchor ? entity : null;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                clear(stack, selection(stack));
                player.sendSystemMessage(Component.translatable("message.tech_tweaks.link_cancelled"));
            }
            return InteractionResult.SUCCESS;
        }
        BlockEntity clicked = endpoint(level, context.getClickedPos());
        if (clicked == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!player.mayBuild() || !level.mayInteract(player, clicked.getBlockPos())
                || !player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack))
            return InteractionResult.FAIL;
        var server = (ServerLevel)level;
        CompoundTag tag = selection(stack);
        String kind = clicked instanceof BlockEntityPylonBase ? "wire" : "pipe";
        if (!tag.contains(START)) {
            tag.putLong(START, clicked.getBlockPos().asLong());
            tag.putString(DIMENSION, level.dimension().identifier().toString());
            tag.putString(KIND, kind);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.sendSystemMessage(Component.translatable("message.tech_tweaks.link_selected",
                    clicked.getBlockPos().toShortString()));
            return InteractionResult.SUCCESS;
        }
        BlockPos firstPos = BlockPos.of(tag.getLongOr(START, 0));
        String dimension = tag.getStringOr(DIMENSION, "");
        String firstKind = tag.getStringOr(KIND, "");
        clear(stack, tag);
        if (!dimension.equals(level.dimension().identifier().toString()) || !kind.equals(firstKind)) {
            player.sendSystemMessage(Component.translatable("message.tech_tweaks.link_mismatch"));
            return InteractionResult.SUCCESS;
        }
        if (firstPos.equals(clicked.getBlockPos())) {
            player.sendSystemMessage(Component.translatable("message.tech_tweaks.link_cancelled"));
            return InteractionResult.SUCCESS;
        }
        BlockEntity first = endpoint(level, firstPos);
        if (first == null || !first.getBlockPos().equals(firstPos)
                || !level.mayInteract(player, firstPos)) {
            player.sendSystemMessage(Component.translatable("message.tech_tweaks.link_unavailable"));
            return InteractionResult.SUCCESS;
        }
        LevelNodeGraph<?> graph;
        if (first instanceof BlockEntityPylonBase && clicked instanceof BlockEntityPylonBase)
            graph = PowerGraph.get(server);
        else if (first instanceof BlockEntityPipeAnchor && clicked instanceof BlockEntityPipeAnchor)
            graph = FluidPipeGraph.graphAt(server, firstPos.asLong());
        else graph = null;
        long a = firstPos.asLong(), b = clicked.getBlockPos().asLong();
        var node = graph == null ? null : graph.getNode(a);
        var peer = graph == null ? null : graph.getNode(b);
        if ((node == null || !node.hasRemoteLinks() || !node.remoteLinks.contains(b))
                && (peer == null || !peer.hasRemoteLinks() || !peer.remoteLinks.contains(a))) {
            player.sendSystemMessage(Component.translatable("message.tech_tweaks.link_missing"));
            return InteractionResult.SUCCESS;
        }
        graph.removeRemoteLink(a, b);
        if (first instanceof BlockEntityPylonBase pylon) pylon.refreshConnections();
        else ((BlockEntityPipeAnchor)first).tickServer();
        if (clicked instanceof BlockEntityPylonBase pylon) pylon.refreshConnections();
        else ((BlockEntityPipeAnchor)clicked).tickServer();
        player.sendSystemMessage(Component.translatable("message.tech_tweaks.link_removed"));
        return InteractionResult.SUCCESS;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                          Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("item.tech_tweaks.virtual_link_cutter.desc"));
        CompoundTag tag = selection(stack);
        if (tag.contains(START)) adder.accept(Component.translatable("message.tech_tweaks.link_selected",
                BlockPos.of(tag.getLongOr(START, 0)).toShortString()));
    }
}
