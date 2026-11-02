package cn.zedocn.tech.mixin;

import cn.zedocn.tech.integration.HbmCraftingGrid;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.client.integrations.jei.transfer.UseCraftingRecipeTransfer", remap = false)
public abstract class AeJeiCraftingGridMixin {
    @Inject(method = "getGuiSlotToIngredientMap", at = @At("HEAD"), cancellable = true)
    private static void tech$statefulShape(Recipe<?> recipe, CallbackInfoReturnable<Map<Integer, Ingredient>> result) {
        var grid = HbmCraftingGrid.matrix(recipe, true);
        if (grid == null) return;
        Map<Integer, Ingredient> slots = new LinkedHashMap<>();
        for (int i = 0; i < grid.size(); i++) if (grid.get(i).isPresent()) slots.put(i, grid.get(i).get());
        result.setReturnValue(slots);
    }
}
