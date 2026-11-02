package cn.zedocn.tech.mixin;

import cn.zedocn.tech.integration.HbmCraftingGrid;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.util.CraftingRecipeUtil", remap = false)
public abstract class AeCraftingGridMixin {
    @Inject(method = "ensure3by3CraftingMatrix", at = @At("HEAD"), cancellable = true)
    private static void tech$statefulShape(Recipe<?> recipe, CallbackInfoReturnable<List<Optional<Ingredient>>> result) {
        var grid = HbmCraftingGrid.matrix(recipe, false);
        if (grid != null) result.setReturnValue(grid);
    }
}
