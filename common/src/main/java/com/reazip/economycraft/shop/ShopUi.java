package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
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
import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;

public final class ShopUi {
    private ShopUi() {}

    private static final ChatFormatting LABEL_PRIMARY_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting LABEL_SECONDARY_COLOR = ChatFormatting.AQUA;
    private static final ChatFormatting VALUE_COLOR = ChatFormatting.DARK_PURPLE;
    private static final ChatFormatting BALANCE_NAME_COLOR = ChatFormatting.YELLOW;
    private static final ChatFormatting BALANCE_LABEL_COLOR = ChatFormatting.GOLD;
    private static final ChatFormatting BALANCE_VALUE_COLOR = ChatFormatting.DARK_PURPLE;

    // ==================== ГРУППИРОВКА ====================
    private static final class Category {
        private final ItemStack template;
        private final List<ShopListing> listings;
        private final int totalItems;
        private final long totalPrice;

        private Category(ItemStack template, List<ShopListing> listings) {
            this.template = template;
            this.listings = listings;
            this.totalItems = listings.stream().mapToInt(l -> l.item.getCount()).sum();
            this.totalPrice = listings.stream().mapToLong(l -> l.price).sum();
        }

        private long totalCostWithTax() {
            double taxRate = EconomyConfig.get().taxRate;
            return listings.stream()
                    .mapToLong(l -> l.price + Math.round(l.price * taxRate))
                    .sum();
        }
    }

    private static List<Category> groupListings(List<ShopListing> listings) {
        Map<ItemKey, List<ShopListing>> map = new LinkedHashMap<>();
        for (ShopListing listing : listings) {
            ItemKey key = new ItemKey(listing.item);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(listing);
        }
        List<Category> result = new ArrayList<>();
        for (Map.Entry<ItemKey, List<ShopListing>> entry : map.entrySet()) {
            result.add(new Category(entry.getKey().getNormalized(), entry.getValue()));
        }
        return result;
    }

    private static final class ItemKey {
        private final ItemStack normalized;
        private final int hash;

        ItemKey(ItemStack stack) {
            this.normalized = stack.copy();
            this.normalized.setCount(1); // игнорируем количество
            // Хеш на основе предмета и всех компонентов (кроме count)
            this.hash = Objects.hash(normalized.getItem(), normalized.getComponents());
        }

        ItemStack getNormalized() { return normalized; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ItemKey other)) return false;
            return ItemStack.isSameItemSameComponents(this.normalized, other.normalized);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
    // ===================================================

    public static void open(ServerPlayer player, ShopManager shop) {
        Component title = Component.literal("Магазин");

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new ShopMenu(id, inv, shop, player, null);
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
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new ShopMenu(id, inv, shop, player, target);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }


    static void openConfirm(ServerPlayer player, ShopManager shop, ShopListing listing) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Подтверждение");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new ConfirmMenu(id, inv, shop, listing, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
    }

    private static void openRemove(ServerPlayer player, ShopManager shop, ShopListing listing) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Снятие");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                try {
                    return new RemoveMenu(id, inv, shop, listing, player);
                } catch (Exception e) {
                    throw e;
                }
            }
        });
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

    private static class ShopMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private List<Category> categories = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private final String target;
        private final Runnable listener = this::updatePage;

        ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer, @Nullable String target) {
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
            List<ShopListing> raw;
            if (target != null) {
                raw = new ArrayList<>(shop.getPlayerListings(viewer, target));
            } else {
                raw = new ArrayList<>(shop.getListings());
            }
            categories = groupListings(raw);
            container.clearContent();
            int start = page * 45;
            int totalPages = (int) Math.ceil(categories.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= categories.size()) break;

                Category cat = categories.get(idx);
                ItemStack display = cat.template.copy();
                display.setCount(Math.min(cat.totalItems, 64));

                List<Component> lore = new ArrayList<>();
                ShopListing first = cat.listings.get(0);
                long taxOne = Math.round(first.price * EconomyConfig.get().taxRate);
                lore.add(createPriceLore(first.price, taxOne));
                lore.add(Component.literal("Объявлений: " + cat.listings.size()).withStyle(ChatFormatting.GRAY));
                long totalCost = cat.totalCostWithTax();
                lore.add(Component.literal("Общая стоимость: " + EconomyCraft.formatMoney(totalCost)).withStyle(ChatFormatting.GOLD));
                lore.add(Component.literal("Нажмите для просмотра, Shift+ПКМ – купить всё").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                display.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lore));

                container.setItem(i, display);
            }

            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Предыдущая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }
            if (start + 45 < categories.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Следующая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }
            container.setItem(navRowStart, createBalanceItem(viewer));
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Страница " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < categories.size()) {
                        Category cat = categories.get(index);
                        ServerPlayer sp = (ServerPlayer) player;
                        if (sp.isShiftKeyDown()) {
                            buyAllCategory(cat, sp);
                            updatePage();
                            return;
                        } else {
                            openCategoryMenu(sp, cat);
                            return;
                        }
                    }
                }
                if (slot == navRowStart + 3 && page > 0) {
                    page--;
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 5 && (page + 1) * 45 < categories.size()) {
                    page++;
                    updatePage();
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        private void buyAllCategory(Category cat, ServerPlayer player) {
            EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
            double taxRate = EconomyConfig.get().taxRate;
            long totalCost = cat.totalCostWithTax();
            if (eco.getBalance(player.getUUID(), true) < totalCost) {
                player.sendSystemMessage(Component.literal("Недостаточно средств для покупки всей категории!").withStyle(ChatFormatting.RED));
                return;
            }
            for (ShopListing listing : cat.listings) {
                if (shop.getListing(listing.id) == null) {
                    player.sendSystemMessage(Component.literal("Некоторые объявления уже недоступны. Покупка отменена.").withStyle(ChatFormatting.RED));
                    return;
                }
            }
            for (ShopListing listing : cat.listings) {
                ShopListing current = shop.getListing(listing.id);
                if (current == null) continue;
                long price = current.price;
                long tax = Math.round(price * taxRate);
                long totalOne = price + tax;
                if (eco.getBalance(player.getUUID(), true) < totalOne) break;
                eco.removeMoney(player.getUUID(), totalOne);
                eco.addMoney(current.seller, price);
                eco.setShopLimit(current.seller, eco.getSellShopLimit(current.seller) + 1);
                ShopListing removed = shop.removeListing(current.id);
                if (removed != null) {
                    shop.notifySellerSale(removed, player);
                    ItemStack stack = removed.item.copy();
                    if (!player.getInventory().add(stack)) {
                        shop.addDelivery(player.getUUID(), stack);
                        ClickEvent ev = ChatCompat.runCommandEvent("/orders claim");
                        if (ev != null) {
                            Component msg = Component.literal("Предмет сохранён: ").withStyle(ChatFormatting.YELLOW)
                                    .append(Component.literal("[Забрать]").withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                            player.sendSystemMessage(msg);
                        } else {
                            ChatCompat.sendRunCommandTellraw(player, "Предмет сохранён: ", "[Забрать]", "/orders claim");
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("Куплено " + stack.getCount() + "x " + stack.getHoverName().getString() +
                                " за " + EconomyCraft.formatMoney(totalOne)).withStyle(ChatFormatting.GREEN));
                    }
                }
            }
            player.sendSystemMessage(Component.literal("Категория полностью выкуплена!").withStyle(ChatFormatting.GREEN));
        }

        private void openCategoryMenu(ServerPlayer player, Category cat) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.literal("Категория");
                }
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new CategoryMenu(id, inv, shop, cat, viewer, target, page);
                }
            });
        }

        @Override
        public boolean stillValid(Player player) { return true; }
        @Override
        public void removed(Player player) {
            super.removed(player);
            shop.removeListener(listener);
        }
        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    // ==================== МЕНЮ КАТЕГОРИИ (отдельные объявления) ====================
    private static class CategoryMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private final List<ShopListing> listings;
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private final String target;
        private final int parentPage;

        CategoryMenu(int id, Inventory inv, ShopManager shop, Category cat, ServerPlayer viewer, String target, int parentPage) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            this.listings = new ArrayList<>(cat.listings);
            this.target = target;
            this.parentPage = parentPage;
            updatePage();

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
            // Кнопка назад в правом нижнем углу
            ItemStack back = new ItemStack(Items.SPECTRAL_ARROW);
            back.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Назад").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED)));
            container.setItem(53, back);

            container.setItem(navRowStart, createBalanceItem(viewer));
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
                        ServerPlayer sp = (ServerPlayer) player;
                        ShopListing current = shop.getListing(listing.id);
                        if (current == null) {
                            sp.sendSystemMessage(Component.literal("Объявление больше не доступно").withStyle(ChatFormatting.RED));
                            updatePage();
                            return;
                        }
                        if (current.seller.equals(player.getUUID())) {
                            openRemove(sp, shop, current);
                        } else {
                            openConfirm(sp, shop, current);
                        }
                        return;
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
                if (slot == 53) {
                    player.closeContainer();
                    if (target != null) openPlayer(viewer, shop, target);
                    else open(viewer, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override
        public boolean stillValid(Player player) { return true; }
        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    // ==================== ОСТАЛЬНЫЕ КЛАССЫ (без изменений) ====================
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
                    @Override
                    public boolean mayPickup(Player player) { return false; }
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
                            eco.setShopLimit(current.seller, eco.getSellShopLimit(current.seller) + 1);
                            eco.removeMoney(player.getUUID(), total);
                            eco.addMoney(current.seller, cost);
                            ShopListing sold = shop.removeListing(current.id);
                            if (sold != null) {
                                shop.notifySellerSale(sold, sp);
                            }
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
                                shop.addDelivery(player.getUUID(), stack);

                                ClickEvent ev = ChatCompat.runCommandEvent("/orders claim");
                                if (ev != null) {
                                    Component msg = Component.literal("Предмет сохранён: ")
                                            .withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal("[Забрать]")
                                                    .withStyle(s -> s.withUnderlined(true)
                                                            .withColor(ChatFormatting.GREEN)
                                                            .withClickEvent(ev)));
                                    sp.sendSystemMessage(msg);
                                } else {
                                    ChatCompat.sendRunCommandTellraw(sp, "Предмет сохранён: ", "[Забрать]", "/orders claim");
                                }
                            } else {
                                sp.sendSystemMessage(
                                        Component.literal("Куплено " + count + "x " + name.getString() + " у " + sellerName +
                                                        " за " + EconomyCraft.formatMoney(total))
                                                .withStyle(ChatFormatting.GREEN)
                                );
                            }
                        }
                    }
                    player.closeContainer();
                    open(sp, shop);
                    return;
                }

                if (slot == 6) {
                    player.closeContainer();
                    open((ServerPlayer) player, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override
        public boolean stillValid(Player player) { return true; }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
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
                this.addSlot(new Slot(container, i, 8 + i * 18, 20) { @Override public boolean mayPickup(Player p) { return false; } });
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
            EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    eco.setShopLimit(player.getUUID(), eco.getSellShopLimit(player.getUUID()) + 1);
                    ShopListing removed = shop.removeListing(listing.id);
                    if (removed != null) {
                        ItemStack stack = removed.item.copy();
                        if (!player.getInventory().add(stack)) {
                            shop.addDelivery(player.getUUID(), stack);

                            ClickEvent ev = ChatCompat.runCommandEvent("/orders claim");
                            if (ev != null) {
                                Component msg = Component.literal("Предмет сохранён: ")
                                        .withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal("[Забрать]")
                                                .withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                                ((ServerPlayer) player).sendSystemMessage(msg);
                            } else {
                                ChatCompat.sendRunCommandTellraw(
                                        (ServerPlayer) player,
                                        "Предмет сохранён: ",
                                        "[Забрать]",
                                        "/orders claim"
                                );
                            }
                        } else {
                            viewer.sendSystemMessage(Component.literal("Объявление снято"));
                        }
                    } else {
                        viewer.sendSystemMessage(Component.literal("Объявление больше не доступно"));
                    }
                    player.closeContainer();
                    open((ServerPlayer) player, shop);
                    return;
                }
                if (slot == 6) {
                    player.closeContainer();
                    open((ServerPlayer) player, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }
}