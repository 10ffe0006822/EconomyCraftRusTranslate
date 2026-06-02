package com.reazip.economycraft.shop;

import com.mojang.authlib.GameProfile;
import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.orders.OrdersUi;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.ProfileComponentCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;

public final class ShopUi {
    private ShopUi() {}

    private static final ChatFormatting LABEL_PRIMARY_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting LABEL_SECONDARY_COLOR = ChatFormatting.AQUA;
    private static final ChatFormatting VALUE_COLOR = ChatFormatting.DARK_PURPLE;
    private static final ChatFormatting BALANCE_NAME_COLOR = ChatFormatting.YELLOW;
    private static final ChatFormatting BALANCE_LABEL_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting BALANCE_VALUE_COLOR = ChatFormatting.DARK_PURPLE;

    public static void open(ServerPlayer player, ShopManager shop) {
        Component title = Component.literal("Магазин");
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return title; }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new ShopMenu(id, inv, shop, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    public static void openPlayer(ServerPlayer player, ShopManager shop, String target) {
        Component title = Component.literal("Магазин");
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return title; }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new ShopPlayerMenu(id, inv, shop, player, target);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }


    private static void openMassConfirm(ServerPlayer player, ShopManager shop, Set<Integer> selectedIds) {
        if (selectedIds.isEmpty()) {
            player.sendSystemMessage(Component.literal("Не выбрано ни одного товара.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() { return Component.literal("Подтверждение массовой покупки"); }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new MassConfirmMenu(id, inv, shop, selectedIds, player);
            }
        });
    }


    private static class MassConfirmMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final Set<Integer> selectedIds;
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(9);
        private long totalPrice;
        private long totalTax;
        private final List<ItemStack> selectedItems = new ArrayList<>();

        MassConfirmMenu(int id, Inventory inv, ShopManager shop, Set<Integer> selectedIds, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.selectedIds = selectedIds;
            this.viewer = viewer;

            Map<String, Integer> itemCounts = new LinkedHashMap<>();
            for (int listingId : selectedIds) {
                ShopListing l = shop.getListing(listingId);
                if (l != null) {
                    totalPrice += l.price;
                    totalTax += Math.round(l.price * EconomyConfig.get().taxRate);
                    selectedItems.add(l.item.copy());
                    String name = l.item.getHoverName().getString();
                    itemCounts.put(name, itemCounts.getOrDefault(name, 0) + l.item.getCount());
                }
            }

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Подтвердить покупку (" + selectedIds.size() + " товаров)")
                            .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack info = new ItemStack(Items.ANVIL);
            info.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Итого: " + EconomyCraft.formatMoney(totalPrice + totalTax))
                            .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Выбранных лотов: " + selectedIds.size()).withStyle(ChatFormatting.YELLOW));
            lore.add(Component.literal("Сумма без налога: " + EconomyCraft.formatMoney(totalPrice)).withStyle(ChatFormatting.YELLOW));
            lore.add(Component.literal("Налог: " + EconomyCraft.formatMoney(totalTax)).withStyle(ChatFormatting.YELLOW));
            lore.add(Component.literal(" ").withStyle(ChatFormatting.WHITE));
            lore.add(Component.literal("Список выбранного:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (Map.Entry<String, Integer> e : itemCounts.entrySet()) {
                lore.add(Component.literal("• " + e.getKey() + " x" + e.getValue()).withStyle(ChatFormatting.AQUA));
            }
            info.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lore));
            container.setItem(4, info);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Отмена").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override public boolean mayPickup(Player p) { return false; }
                });
            }

            int y = 40;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    performMassPurchase();
                    player.closeContainer();
                    ShopUi.open(viewer, shop);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    ShopUi.open(viewer, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        private void performMassPurchase() {
            EconomyManager eco = EconomyCraft.getManager(viewer.level().getServer());
            long total = totalPrice + totalTax;
            long balance = eco.getBalance(viewer.getUUID(), true);
            if (balance < total) {
                viewer.sendSystemMessage(Component.literal("Недостаточно средств. Требуется: " + EconomyCraft.formatMoney(total))
                        .withStyle(ChatFormatting.RED));
                return;
            }

            List<ShopListing> toBuy = new ArrayList<>();
            for (int id : selectedIds) {
                ShopListing l = shop.getListing(id);
                if (l == null) {
                    viewer.sendSystemMessage(Component.literal("Один из товаров больше не доступен. Попробуйте снова.")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                toBuy.add(l);
            }

            if (!eco.removeMoney(viewer.getUUID(), total)) {
                viewer.sendSystemMessage(Component.literal("Не удалось списать средства. Повторите позже.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            boolean hadStorage = false;
            for (ShopListing l : toBuy) {
                eco.addMoney(l.seller, l.price);
                shop.removeListing(l.id);
                shop.notifySellerSale(l, viewer);

                ItemStack stack = l.item.copy();
                if (!viewer.getInventory().add(stack)) {
                    if (!stack.isEmpty()) {
                        shop.addDelivery(viewer.getUUID(), stack);
                        hadStorage = true;
                    }
                }
            }

            shop.save();

            viewer.sendSystemMessage(Component.literal("Вы купили " + toBuy.size() + " товаров за " + EconomyCraft.formatMoney(total))
                    .withStyle(ChatFormatting.GREEN));

            if (hadStorage) {
                ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                if (ev != null) {
                    Component msg = Component.literal("Часть предметов сохранена в доставках: ")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("[Забрать]")
                                    .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                    viewer.sendSystemMessage(msg);
                } else {
                    ChatCompat.sendRunCommandTellraw(viewer, "Часть предметов сохранена: ", "[Забрать]", "/eco orders claim");
                }
            }
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class ShopMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private List<ShopListing> listings = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private boolean selectionMode = false;
        private final Set<Integer> selectedIds = new HashSet<>();
        private final Runnable listener = this::updatePage;

        ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            updatePage();
            shop.addListener(listener);

            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player player) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
            int y = 18 + 6 * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            listings = new ArrayList<>(shop.getListings());
            container.clearContent();
            int start = page * 45;
            int totalPages = (int) Math.ceil(listings.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= listings.size()) break;

                ShopListing l = listings.get(idx);
                ItemStack display = l.item.copy();

                String sellerName;
                ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(l.seller);
                if (sellerPlayer != null) {
                    sellerName = IdentityCompat.of(sellerPlayer).name();
                } else {
                    sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(l.seller);
                }

                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                List<Component> lore = new ArrayList<>();
                lore.add(createPriceLore(l.price, tax));
                lore.add(labeledValue("Продавец", sellerName, LABEL_SECONDARY_COLOR));
                if (selectionMode && selectedIds.contains(l.id)) {
                    lore.add(Component.literal("✓ ВЫДЕЛЕН").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
                display.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lore));

                if (selectionMode && selectedIds.contains(l.id)) {
                    display.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Предыдущая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }
            if (start + 45 < listings.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Следующая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            ItemStack selectToggle = new ItemStack(selectionMode ? Items.RED_DYE : Items.GREEN_DYE);
            selectToggle.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal(selectionMode ? "Выключить выделение" : "Включить выделение")
                            .withStyle(s -> s.withItalic(false).withColor(selectionMode ? ChatFormatting.RED : ChatFormatting.GREEN)));
            container.setItem(navRowStart + 2, selectToggle);

            ItemStack delivery = new ItemStack(Items.CHEST);
            delivery.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Доставка").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.BLUE)));
            container.setItem(navRowStart + 1, delivery);

            if (selectionMode && !selectedIds.isEmpty()) {
                ItemStack pay = new ItemStack(Items.ANVIL);
                pay.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        Component.literal("Оплатить (" + selectedIds.size() + ")")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));

                long totalPrice = 0;
                long totalTax = 0;
                Map<String, Integer> itemCounts = new LinkedHashMap<>();
                for (int id : selectedIds) {
                    ShopListing l = shop.getListing(id);
                    if (l != null) {
                        totalPrice += l.price;
                        totalTax += Math.round(l.price * EconomyConfig.get().taxRate);
                        String name = l.item.getHoverName().getString();
                        itemCounts.put(name, itemCounts.getOrDefault(name, 0) + l.item.getCount());
                    }
                }
                long total = totalPrice + totalTax;
                List<Component> lorePay = new ArrayList<>();
                lorePay.add(Component.literal("Итого: " + EconomyCraft.formatMoney(total)).withStyle(ChatFormatting.YELLOW));
                lorePay.add(Component.literal("Налог: " + EconomyCraft.formatMoney(totalTax)).withStyle(ChatFormatting.YELLOW));
                lorePay.add(Component.literal(" ").withStyle(ChatFormatting.WHITE));
                lorePay.add(Component.literal("Выбрано:").withStyle(ChatFormatting.GOLD));
                for (Map.Entry<String, Integer> e : itemCounts.entrySet()) {
                    lorePay.add(Component.literal("• " + e.getKey() + " x" + e.getValue()).withStyle(ChatFormatting.AQUA));
                }
                pay.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lorePay));
                container.setItem(navRowStart + 6, pay);
            } else {
                container.setItem(navRowStart + 6, ItemStack.EMPTY);
            }

            ItemStack balance = createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Страница " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < listings.size()) {
                        ShopListing listing = listings.get(index);
                        if (selectionMode) {
                            if (selectedIds.contains(listing.id)) {
                                selectedIds.remove(listing.id);
                            } else {
                                selectedIds.add(listing.id);
                            }
                            updatePage();
                            return;
                        } else {
                            if (listing.seller.equals(player.getUUID())) {
                                openRemove((ServerPlayer) player, shop, listing);
                            } else {
                                openConfirm((ServerPlayer) player, shop, listing);
                            }
                            return;
                        }
                    }
                }
                if (slot == navRowStart + 3 && page > 0) {
                    page--;
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 5 && (page + 1) * 45 < listings.size()) {
                    page++;
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 2) {
                    selectionMode = !selectionMode;
                    if (!selectionMode) selectedIds.clear();
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 1) {
                    EconomyManager eco = EconomyCraft.getManager(viewer.level().getServer());
                    OrdersUi.openClaims(viewer, eco);
                    return;
                }
                if (slot == navRowStart + 6 && selectionMode && !selectedIds.isEmpty()) {
                    openMassConfirm(viewer, shop, new HashSet<>(selectedIds));
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public void removed(Player player) {
            super.removed(player);
            shop.removeListener(listener);
        }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class ShopPlayerMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private List<ShopListing> listings = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private final String target;
        private final Runnable listener = this::updatePage;

        ShopPlayerMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer, String target) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            this.target = target;
            updatePage();
            shop.addListener(listener);

            for (int i = 0; i < 54; i++) {
                int r = i / 9;
                int c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player player) { return false; }
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                });
            }
            int y = 18 + 6 * 18 + 14;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        private void updatePage() {
            listings = new ArrayList<>(shop.getPlayerListings(viewer, target));
            container.clearContent();
            int start = page * 45;
            int totalPages = (int) Math.ceil(listings.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= listings.size()) break;

                ShopListing l = listings.get(idx);
                ItemStack display = l.item.copy();

                String sellerName;
                ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(l.seller);
                if (sellerPlayer != null) {
                    sellerName = IdentityCompat.of(sellerPlayer).name();
                } else {
                    sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(l.seller);
                }
                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                display.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(List.of(
                        createPriceLore(l.price, tax),
                        labeledValue("Продавец", sellerName, LABEL_SECONDARY_COLOR))));
                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Предыдущая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }
            if (start + 45 < listings.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Следующая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            ItemStack balance = createBalanceItem(viewer);
            container.setItem(navRowStart, balance);

            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Страница " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < listings.size()) {
                        ShopListing listing = listings.get(index);
                        if (listing.seller.equals(player.getUUID())) {
                            openRemove((ServerPlayer) player, shop, listing);
                        } else {
                            openConfirm((ServerPlayer) player, shop, listing);
                        }
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * 45 < listings.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public void removed(Player player) {
            super.removed(player);
            shop.removeListener(listener);
        }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }


    private static void openConfirm(ServerPlayer player, ShopManager shop, ShopListing listing) {
        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.literal("Подтверждение"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ConfirmMenu(id, inv, shop, listing, player);
            }
        });
    }

    private static void openRemove(ServerPlayer player, ShopManager shop, ShopListing listing) {
        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.literal("Снятие"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new RemoveMenu(id, inv, shop, listing, player);
            }
        });
    }

    private static class ConfirmMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ShopListing listing;
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(9);

        ConfirmMenu(int id, Inventory inv, ShopManager shop, ShopListing listing, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.listing = listing;
            this.viewer = viewer;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Подтвердить").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            String sellerName;
            ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(listing.seller);
            if (sellerPlayer != null) {
                sellerName = IdentityCompat.of(sellerPlayer).name();
            } else {
                sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(listing.seller);
            }

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(List.of(
                    createPriceLore(listing.price, tax),
                    labeledValue("Продавец", sellerName, LABEL_SECONDARY_COLOR))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Отмена").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            }

            int y = 40;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    ShopListing current = shop.getListing(listing.id);
                    ServerPlayer sp = (ServerPlayer) player;
                    var server = sp.level().getServer();

                    if (current == null) {
                        sp.sendSystemMessage(Component.literal("Объявление больше не доступно").withStyle(ChatFormatting.RED));
                    } else {
                        EconomyManager eco = EconomyCraft.getManager(server);
                        long cost = current.price;
                        long tax = Math.round(cost * EconomyConfig.get().taxRate);
                        long total = cost + tax;
                        long bal = eco.getBalance(player.getUUID(), true);

                        if (bal < total) {
                            sp.sendSystemMessage(Component.literal("Недостаточно средств").withStyle(ChatFormatting.RED));
                        } else {
                            eco.removeMoney(player.getUUID(), total);
                            eco.addMoney(current.seller, cost);
                            ShopListing sold = shop.removeListing(current.id);
                            if (sold != null) shop.notifySellerSale(sold, sp);
                            ItemStack stack = current.item.copy();
                            int count = stack.getCount();
                            Component name = stack.getHoverName();

                            String sellerName;
                            ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(current.seller);
                            if (sellerPlayer != null) {
                                sellerName = IdentityCompat.of(sellerPlayer).name();
                            } else {
                                sellerName = eco.getBestName(current.seller);
                            }

                            if (!player.getInventory().add(stack)) {
                                // Если не добавился полностью – отправляем остаток в доставку
                                if (!stack.isEmpty()) {
                                    shop.addDelivery(player.getUUID(), stack);
                                    ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                                    if (ev != null) {
                                        Component msg = Component.literal("Предмет сохранён в доставке: ")
                                                .withStyle(ChatFormatting.YELLOW)
                                                .append(Component.literal("[Забрать]")
                                                        .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                                        sp.sendSystemMessage(msg);
                                    } else {
                                        ChatCompat.sendRunCommandTellraw(sp, "Предмет сохранён в доставке: ", "[Забрать]", "/eco orders claim");
                                    }
                                } else {
                                    sp.sendSystemMessage(Component.literal("Куплено " + count + "x " + name.getString() + " у " + sellerName +
                                            " за " + EconomyCraft.formatMoney(total)).withStyle(ChatFormatting.GREEN));
                                }
                            } else {
                                sp.sendSystemMessage(Component.literal("Куплено " + count + "x " + name.getString() + " у " + sellerName +
                                        " за " + EconomyCraft.formatMoney(total)).withStyle(ChatFormatting.GREEN));
                            }
                        }
                    }
                    player.closeContainer();
                    ShopUi.open(sp, shop);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    ShopUi.open((ServerPlayer) player, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static class RemoveMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ShopListing listing;
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(9);

        RemoveMenu(int id, Inventory inv, ShopManager shop, ShopListing listing, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x1, id);
            this.shop = shop;
            this.listing = listing;
            this.viewer = viewer;

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Подтвердить").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(List.of(
                    createPriceLore(listing.price, tax),
                    labeledValue("Продавец", "вы", LABEL_SECONDARY_COLOR),
                    Component.literal("Это снимет объявление").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED)))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("Отмена").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) {
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                    @Override public boolean mayPickup(Player p) { return false; }
                });
            }

            int y = 40;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
                }
            }
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
            }
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    ShopListing removed = shop.removeListing(listing.id);
                    if (removed != null) {
                        ItemStack stack = removed.item.copy();
                        if (!player.getInventory().add(stack)) {
                            if (!stack.isEmpty()) {
                                shop.addDelivery(player.getUUID(), stack);
                                ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                                if (ev != null) {
                                    Component msg = Component.literal("Предмет сохранён в доставке: ")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal("[Забрать]")
                                                    .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                                    ((ServerPlayer) player).sendSystemMessage(msg);
                                } else {
                                    ChatCompat.sendRunCommandTellraw((ServerPlayer) player, "Предмет сохранён в доставке: ", "[Забрать]", "/eco orders claim");
                                }
                            } else {
                                viewer.sendSystemMessage(Component.literal("Объявление снято"));
                            }
                        } else {
                            viewer.sendSystemMessage(Component.literal("Объявление снято"));
                        }
                    } else {
                        viewer.sendSystemMessage(Component.literal("Объявление больше не доступно"));
                    }
                    player.closeContainer();
                    ShopUi.open((ServerPlayer) player, shop);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    ShopUi.open((ServerPlayer) player, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }


    private static Component createPriceLore(long price, long tax) {
        StringBuilder value = new StringBuilder(EconomyCraft.formatMoney(price));
        if (tax > 0) {
            value.append(" (+").append(EconomyCraft.formatMoney(tax)).append(" налог)");
        }
        return labeledValue("Цена", value.toString(), LABEL_PRIMARY_COLOR);
    }

    private static ItemStack createBalanceItem(ServerPlayer player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        GameProfile profile = player.getGameProfile();
        ProfileComponentCompat.tryResolvedOrUnresolved(profile).ifPresent(resolvable ->
                head.set(net.minecraft.core.component.DataComponents.PROFILE, resolvable));
        long balance = EconomyCraft.getManager(player.level().getServer()).getBalance(player.getUUID(), true);
        head.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal(IdentityCompat.of(player).name()).withStyle(s -> s.withItalic(false).withColor(BALANCE_NAME_COLOR)));
        head.set(net.minecraft.core.component.DataComponents.LORE,
                new ItemLore(List.of(balanceLore(balance))));
        return head;
    }

    private static Component balanceLore(long balance) {
        return Component.literal("Баланс: ")
                .withStyle(s -> s.withItalic(false).withColor(BALANCE_LABEL_COLOR))
                .append(Component.literal(EconomyCraft.formatMoney(balance))
                        .withStyle(s -> s.withItalic(false).withColor(BALANCE_VALUE_COLOR)));
    }

    private static Component labeledValue(String label, String value, ChatFormatting labelColor) {
        return Component.literal(label + ": ")
                .withStyle(s -> s.withItalic(false).withColor(labelColor))
                .append(Component.literal(value)
                        .withStyle(s -> s.withItalic(false).withColor(VALUE_COLOR)));
    }
}