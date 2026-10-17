package cn.zedocn.tech.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.*;
import top.theillusivec4.curios.common.inventory.container.CuriosMenuProvider;
import top.theillusivec4.curios.common.network.server.sync.*;
import top.theillusivec4.curios.impl.CuriosRegistry;

public final class CuriosSupport {
    private static final Logger LOGGER=LogUtils.getLogger();
    public static String describe(ServerPlayer player) {
        var attachment=player.getExistingDataOrNull(CuriosRegistry.INVENTORY.get());
        var handler=CuriosApi.getCuriosInventory(player).orElse(null);
        int equipped=0;
        if(attachment!=null) for(var stacks:attachment.asMap().values()) for(int i=0;i<stacks.getStacks().getSlots();i++) if(!stacks.getStacks().getStackInSlot(i).isEmpty()) equipped++;
        return "configured="+CuriosSlotTypes.getDefaultEntitySlotTypes(player).size()+", attachment="+(attachment!=null)+", capability="+(handler!=null)+", visible="+(handler==null?0:handler.getVisibleSlots())+", storedGroups="+(attachment==null?0:attachment.asMap().size())+", equipped="+equipped;
    }
    public static void logOpen(ServerPlayer player) { LOGGER.info("[TECH Curios] open player={} {}",player.getName().getString(),describe(player)); }
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("tech").then(Commands.literal("curios")
            .then(Commands.literal("inspect").executes(c -> {
                var p=c.getSource().getPlayerOrException(); var info=describe(p);
                LOGGER.info("[TECH Curios] inspect player={} {}",p.getName().getString(),info);
                c.getSource().sendSuccess(() -> Component.literal(info),false); return 1;
            }))
            .then(Commands.literal("resync").executes(c -> {
                var p=c.getSource().getPlayerOrException(); var handler=CuriosApi.getCuriosInventory(p).orElse(null);
                if(handler==null || handler.getVisibleSlots()==0) {
                    logOpen(p); c.getSource().sendFailure(Component.literal("服务端饰品库存不可用，已记录诊断；未改动饰品数据。")); return 0;
                }
                SPacketSyncData.send(p);
                PacketDistributor.sendToPlayer(p,new SPacketSyncCurios(p.getId(),handler.getCurios()));
                // Vanilla returns the cursor item to the player's inventory when replacing a menu.
                if(p.openMenu(new CuriosMenuProvider()).isEmpty()) return 0;
                c.getSource().sendSuccess(() -> Component.literal("已重新同步原有饰品并打开界面。"),false); return 1;
            }))));
    }
}
