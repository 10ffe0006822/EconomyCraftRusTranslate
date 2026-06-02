package com.reazip.economycraft.shop;

import com.mojang.authlib.GameProfile;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ProfileComponentCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class ShopUi {
    private ShopUi() {}

    static final ChatFormatting LABEL_PRIMARY_COLOR = ChatFormatting.GOLD;
    static final ChatFormatting LABEL_SECONDARY_COLOR = ChatFormatting.AQUA;
    static final ChatFormatting VALUE_COLOR = ChatFormatting.DARK_PURPLE;
    static final ChatFormatting BALANCE_NAME_COLOR = ChatFormatting.YELLOW;
    static final ChatFormatting BALANCE_LABEL_COLOR = ChatFormatting.GOLD;
    static final ChatFormatting BALANCE_VALUE_COLOR = ChatFormatting.DARK_PURPLE;

    public static void open(ServerPlayer player, ShopManager shop) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Магазин"); }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ShopMenus.ShopMenu(id, inv, shop, player);
            }
        });
    }

    public static void openPlayer(ServerPlayer player, ShopManager shop, String target) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Магазин игрока"); }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ShopMenus.ShopPlayerMenu(id, inv, shop, player, target);
            }
        });
    }

    static Component createPriceLore(long price, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(price));
        if (tax > 0) {
            value.append(" (+").append(EconomyCraft.formatMoney(tax)).append(" налог)");
        }
        return labeledValue("Цена", value.toString(), LABEL_PRIMARY_COLOR);
    }

    static ItemStack createBalanceItem(ServerPlayer player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        GameProfile profile = player.getGameProfile();
        ProfileComponentCompat.tryResolvedOrUnresolved(profile).ifPresent(resolvable ->
                head.set(DataComponents.PROFILE, resolvable));
        long balance = EconomyCraft.getManager(player.level().getServer()).getBalance(player.getUUID(), true);
        head.set(DataComponents.CUSTOM_NAME,
                Component.literal(IdentityCompat.of(player).name()).withStyle(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(DataComponents.LORE, new ItemLore(List.of(balanceLore(balance))));
        return head;
    }

    static Component balanceLore(long balance) {
        return Component.literal("Баланс: ")
                .withStyle(s -> s.withItalic(false).withColor(BALANCE_LABEL_COLOR))
                .append(Component.literal(EconomyCraft.formatMoney(balance))
                        .withStyle(s -> s.withItalic(false).withColor(BALANCE_VALUE_COLOR)));
    }

    static Component labeledValue(String label, String value, ChatFormatting labelColor) {
        return Component.literal(label + ": ")
                .withStyle(s -> s.withItalic(false).withColor(labelColor))
                .append(Component.literal(value)
                        .withStyle(s -> s.withItalic(false).withColor(VALUE_COLOR)));
    }
}