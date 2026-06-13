package com.hunted.mod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CursedCrownItem extends Item {

    public static final String REGISTRY_NAME = "cursed_crown";

    public CursedCrownItem() {
        super(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.EPIC)
            .fireResistant()
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§5§oAn ancient crown forged in the void."));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.literal("§cWhile worn, all shall know your location."));
        tooltip.add(Component.literal("§7Survive for §c1 hour§7 to claim victory."));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.literal("§8§o\"The hunt never ends.\""));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // enchant glint
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("§5§lCursed Crown");
    }
}
