package cn.zedocn.tech;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import cn.zedocn.tech.mixin.VaultServerAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public final class VaultAutomation {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("d4ae34e1-a665-4ad5-a7bd-2a7187241098"),"TECH_Automation");
    public static void tick(ServerLevel level,ControllerBlockEntity controller) {
        var pos=controller.targetPos();
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof VaultBlockEntity vault) || AutomationLookup.controller(level,pos,ControllerKind.VAULT)!=controller) { controller.status=1; return; }
        var state=vault.getBlockState(); var stage=state.getValue(VaultBlock.STATE);
        if (stage==VaultState.UNLOCKING || stage==VaultState.EJECTING) { controller.status=3; return; }
        var config=vault.getConfig(); var key=controller.getItem(0);
        if (config.keyItem().isEmpty() || !ItemStack.isSameItemSameComponents(key,config.keyItem()) || key.getCount()<config.keyItem().getCount()) { controller.status=4; return; }
        boolean empty=false; for(int i=1;i<9;i++) if(controller.getItem(i).isEmpty()) empty=true;
        if (!empty) { controller.status=7; return; }
        if (controller.energy.getAmountAsLong()<5_000) { controller.status=2; return; }
        var player=FakePlayerFactory.get(level,PROFILE);
        player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        var loot=VaultServerAccess.techResolve(level,config,pos,player,key);
        if (loot.isEmpty()) return;
        controller.spend(5_000); controller.removeItem(0,config.keyItem().getCount());
        VaultServerAccess.techUnlock(level,state,pos,config,vault.getServerData(),vault.getSharedData(),loot);
        vault.setChanged(); controller.status=0;
    }
}
