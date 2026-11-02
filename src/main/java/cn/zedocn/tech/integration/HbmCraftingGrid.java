package cn.zedocn.tech.integration;

import com.hbm.inventory.recipes.crafting.StateShapedRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;

/** Expands NTM's stateful shaped recipes without losing blank cells or row width. */
public final class HbmCraftingGrid {
    private HbmCraftingGrid() {}

    public static List<Optional<Ingredient>> matrix(Recipe<?> recipe, boolean centered) {
        if (!(recipe instanceof StateShapedRecipe)) return null;
        var displays = recipe.display();
        if (displays.isEmpty() || !(displays.getFirst() instanceof ShapedCraftingRecipeDisplay shape)) return null;
        int width = shape.width(), height = shape.height();
        if (width < 1 || width > 3 || height < 1 || height > 3) return null;
        var placement = recipe.placementInfo();
        var indices = placement.slotsToIngredientIndex();
        if (placement.isImpossibleToPlace() || indices.size() != width * height) return null;
        List<Optional<Ingredient>> result = new ArrayList<>(java.util.Collections.nCopies(9, Optional.empty()));
        int dx = centered && width == 1 ? 1 : 0;
        int dy = centered && height == 1 ? 1 : centered && width == 3 && height == 2 ? 1 : 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int index = indices.getInt(x + y * width);
            if (index >= 0) result.set(x + dx + (y + dy) * 3, Optional.of(placement.ingredients().get(index)));
        }
        return result;
    }
}
