package net.caduzz.tablecraft.block;

import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

/**
 * Fogos na vitória: brancos claros ou pretos/cinza-escuro.
 */
public final class CheckersVictoryEffects {

    private CheckersVictoryEffects() {
    }

    public static void spawnVictoryFireworks(ServerLevel level, BlockPos pos, boolean whiteWins) {
        int main = whiteWins ? 0xFFFFFF : 0x1A1A1A;
        int accent = whiteWins ? 0xDDEEFF : 0x0A0A0A;
        FireworkExplosion burst = new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL,
                IntArrayList.of(main, accent),
                new IntArrayList(),
                true,
                true);
        FireworkExplosion star = new FireworkExplosion(
                FireworkExplosion.Shape.STAR,
                IntArrayList.of(whiteWins ? 0xF0F8FF : 0x2D2D2D),
                new IntArrayList(),
                false,
                true);
        ItemStack template = new ItemStack(Items.FIREWORK_ROCKET);
        template.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(burst, star)));

        double bx = pos.getX() + 0.5;
        double by = pos.getY() + 0.9;
        double bz = pos.getZ() + 0.5;
        for (int i = 0; i < 8; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 3.0;
            double oz = (level.random.nextDouble() - 0.5) * 3.0;
            double oy = level.random.nextDouble() * 0.4;
            ItemStack copy = template.copy();
            FireworkRocketEntity rocket = new FireworkRocketEntity(level, bx + ox, by + oy, bz + oz, copy);
            level.addFreshEntity(rocket);
        }
    }
}
