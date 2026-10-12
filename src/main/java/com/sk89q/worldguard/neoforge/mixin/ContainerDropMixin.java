package com.sk89q.worldguard.neoforge.mixin;

import com.sk89q.worldguard.neoforge.protection.ProtectionHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class ContainerDropMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void worldguard$drop(int slot, int button, ClickType type, Player player, CallbackInfo callback) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        boolean dropping = type == ClickType.THROW || slot == -999 && type == ClickType.PICKUP;
        ItemStack candidate = slot >= 0 && slot < menu.slots.size() ? menu.getSlot(slot).getItem() : menu.getCarried();
        boolean denied = dropping && (!ProtectionHooks.allowDrop(player) || !ProtectionHooks.blacklist(player, "on-drop", candidate));
        if (!denied && slot >= 0 && slot < menu.slots.size()) {
            var selected = menu.getSlot(slot);
            boolean armorSlot = selected.container instanceof Inventory && selected.getSlotIndex() >= 36 && selected.getSlotIndex() < 40;
            ItemStack incoming = type == ClickType.SWAP && button >= 0 && button < player.getInventory().getContainerSize()
                    ? player.getInventory().getItem(button) : menu.getCarried();
            if (armorSlot && !incoming.isEmpty()) denied = !ProtectionHooks.blacklist(player, "on-equip", incoming);
            else if (type == ClickType.QUICK_MOVE && menu instanceof InventoryMenu && Equipable.get(candidate) != null)
                denied = !ProtectionHooks.blacklist(player, "on-equip", candidate);
        }
        if (denied) {
            callback.cancel();
            menu.broadcastChanges();
        }
    }
}
