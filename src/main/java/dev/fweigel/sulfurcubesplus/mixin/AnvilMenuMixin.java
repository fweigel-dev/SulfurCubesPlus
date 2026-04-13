package dev.fweigel.sulfurcubesplus.mixin;

import dev.fweigel.sulfurcubesplus.ISulfurCubeAnvilMenu;
import dev.fweigel.sulfurcubesplus.SulfurCubeEntityAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin implements ISulfurCubeAnvilMenu {

    @Unique
    private SulfurCubeEntityAccess sulfurcubesplus$entityAccess = null;

    @Override
    public SulfurCubeEntityAccess sulfurcubesplus$getEntityAccess() {
        return sulfurcubesplus$entityAccess;
    }

    @Override
    public void sulfurcubesplus$setEntityAccess(SulfurCubeEntityAccess access) {
        this.sulfurcubesplus$entityAccess = access;
    }

    /**
     * After the result has been taken from an entity-held anvil, replicate vanilla's
     * 12 % damage chance and play the correct sound. Never fires for placed anvils.
     */
    @Inject(method = "onTake", at = @At("TAIL"))
    private void sulfurcubesplus$damageAnvilOnUse(Player player, ItemStack stack, CallbackInfo ci) {
        if (sulfurcubesplus$entityAccess == null) return;

        SulfurCube cube = sulfurcubesplus$entityAccess.cube;
        if (!cube.isAlive()) return;

        ItemStack bodyItem = cube.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty()) return;

        BlockPos pos = cube.blockPosition();

        if (cube.level().getRandom().nextFloat() < 0.12f) {
            if (bodyItem.is(Items.ANVIL)) {
                cube.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.CHIPPED_ANVIL));
            } else if (bodyItem.is(Items.CHIPPED_ANVIL)) {
                cube.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DAMAGED_ANVIL));
            } else {
                cube.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
                cube.level().levelEvent(1029, pos, 0); // anvil destroy sound
                return;
            }
        }

        cube.level().levelEvent(1030, pos, 0); // anvil use sound
    }
}
