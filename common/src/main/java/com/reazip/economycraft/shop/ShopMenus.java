package com.reazip.economycraft.shop;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyConfig;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.orders.OrdersUi;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.IdentityCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.function.Consumer;

public final class ShopMenus {
    private ShopMenus() {}

    // =====================================================================
    // === ВСПОМОГАТЕЛЬНЫЕ СТРУКТУРЫ =======================================
    // =====================================================================
    private static final class ItemKey {
        private final ItemStack template;
        private final int hashCode;
        ItemKey(ItemStack stack) {
            this.template = stack.copyWithCount(1);
            this.hashCode = template.getItem().hashCode() * 31 + Objects.hashCode(template.getComponents());
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemKey other)) return false;
            return ItemStack.isSameItemSameComponents(template, other.template);
        }
        @Override public int hashCode() { return hashCode; }
        public ItemStack getTemplate() { return template; }
    }

    public enum SortType {
        PRICE_ASC, PRICE_DESC, NEWEST, OLDEST, QUANTITY_ASC, QUANTITY_DESC;

        private static final SortType[] VALUES = values();
        public SortType next() { return VALUES[(ordinal() + 1) % VALUES.length]; }
        public SortType prev() { return VALUES[(ordinal() - 1 + VALUES.length) % VALUES.length]; }
    }

    private static class PlayerPrefs {
        SortType sortType = SortType.NEWEST;
    }
    private static final Map<UUID, PlayerPrefs> prefsCache = new HashMap<>();
    private static PlayerPrefs getPrefs(ServerPlayer player) {
        return prefsCache.computeIfAbsent(player.getUUID(), k -> new PlayerPrefs());
    }
    private static void savePrefs(ServerPlayer player, PlayerPrefs prefs) {
        prefsCache.put(player.getUUID(), prefs);
    }

    private static void openMassConfirm(ServerPlayer player, ShopManager shop, Set<Integer> selectedIds) {
        if (selectedIds.isEmpty()) {
            player.sendSystemMessage(Component.literal("Не выбрано ни одного товара.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.literal("Подтверждение массовой покупки"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new MassConfirmMenu(id, inv, shop, selectedIds, player);
            }
        });
    }

    private static class MassConfirmMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final Set<Integer> selectedIds;
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(9);
        private long totalPrice, totalTax;

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
                    String name = l.item.getHoverName().getString();
                    itemCounts.put(name, itemCounts.getOrDefault(name, 0) + l.item.getCount());
                }
            }

            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Подтвердить покупку (" + selectedIds.size() + " товаров)")
                            .withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack info = new ItemStack(Items.ANVIL);
            info.set(DataComponents.CUSTOM_NAME,
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
            info.set(DataComponents.LORE, new ItemLore(lore));
            container.setItem(4, info);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(DataComponents.CUSTOM_NAME,
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

    public static class ShopMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private boolean selectionMode = false;
        private final SelectionManager selection = new SelectionManager(); // отдельный класс
        private final Runnable listener = this::updatePage;

        private List<Map.Entry<ItemKey, List<ShopListing>>> groupedEntries = new ArrayList<>();
        private final Map<Integer, List<ShopListing>> slotToGroup = new HashMap<>();
        private SortType currentSort;

        public ShopMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            this.currentSort = getPrefs(viewer).sortType;
            updatePage();
            shop.addListener(listener);
            for (int i = 0; i < 54; i++) {
                int r = i / 9, c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
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
            Map<ItemKey, List<ShopListing>> groupMap = new LinkedHashMap<>();
            for (ShopListing l : shop.getListings()) {
                ItemKey key = new ItemKey(l.item);
                groupMap.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
            }
            List<Map.Entry<ItemKey, List<ShopListing>>> sortedGroups = new ArrayList<>(groupMap.entrySet());
            Comparator<Map.Entry<ItemKey, List<ShopListing>>> comp = getGroupComparator();
            if (comp != null) sortedGroups.sort(comp);
            groupedEntries = sortedGroups;

            container.clearContent();
            slotToGroup.clear();
            int start = page * 45;
            int totalPages = (int) Math.ceil(groupedEntries.size() / 45.0);

            for (int i = 0; i < 45; i++) {
                int idx = start + i;
                if (idx >= groupedEntries.size()) break;
                Map.Entry<ItemKey, List<ShopListing>> entry = groupedEntries.get(idx);
                List<ShopListing> listings = entry.getValue();
                ItemStack display = entry.getKey().getTemplate().copy();

                long minPrice = listings.stream().mapToLong(l -> l.price).min().orElse(0);
                long maxPrice = listings.stream().mapToLong(l -> l.price).max().orElse(0);
                int totalItems = listings.stream().mapToInt(l -> l.item.getCount()).sum();
                int sellerCount = (int) listings.stream().map(l -> l.seller).distinct().count();

                List<Component> lore = new ArrayList<>();
                if (minPrice == maxPrice) {
                    lore.add(ShopUi.labeledValue("Цена", EconomyCraft.formatMoney(minPrice), ShopUi.LABEL_PRIMARY_COLOR));
                } else {
                    lore.add(Component.literal("Цена: " + EconomyCraft.formatMoney(minPrice) + " - " + EconomyCraft.formatMoney(maxPrice))
                            .withStyle(ShopUi.VALUE_COLOR));
                }
                lore.add(ShopUi.labeledValue("Всего предметов", String.valueOf(totalItems), ShopUi.LABEL_SECONDARY_COLOR));
                lore.add(ShopUi.labeledValue("Продавцов", String.valueOf(sellerCount), ShopUi.LABEL_SECONDARY_COLOR));
                if (selectionMode && selectedContainsAny(listings)) {
                    lore.add(Component.literal("✓ ВЫДЕЛЕН (частично)").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
                display.set(DataComponents.LORE, new ItemLore(lore));
                container.setItem(i, display);
                slotToGroup.put(i, listings);
            }

            // Навигация
            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Предыдущая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }
            if (start + 45 < groupedEntries.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Следующая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            // Кнопка переключения режима выделения
            ItemStack selectToggle = new ItemStack(selectionMode ? Items.RED_DYE : Items.GREEN_DYE);
            selectToggle.set(DataComponents.CUSTOM_NAME,
                    Component.literal(selectionMode ? "Выключить выделение" : "Включить выделение")
                            .withStyle(s -> s.withItalic(false).withColor(selectionMode ? ChatFormatting.RED : ChatFormatting.GREEN)));
            container.setItem(navRowStart + 2, selectToggle);

            // Сортировка
            ItemStack sortButton = new ItemStack(Items.COMPASS);
            String sortLabel = switch (currentSort) {
                case PRICE_ASC -> "Цена ↑";
                case PRICE_DESC -> "Цена ↓";
                case NEWEST -> "Новые ↓";
                case OLDEST -> "Старые ↑";
                case QUANTITY_ASC -> "Кол-во ↑";
                case QUANTITY_DESC -> "Кол-во ↓";
            };
            sortButton.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Сортировка: " + sortLabel).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
            container.setItem(navRowStart + 7, sortButton);

            // Доставка
            ItemStack delivery = new ItemStack(Items.CHEST);
            delivery.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Доставка").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.BLUE)));
            container.setItem(navRowStart + 1, delivery);

            // Оплата выделенного
            if (selectionMode && !selection.isEmpty()) {
                ItemStack pay = new ItemStack(Items.ANVIL);
                pay.set(DataComponents.CUSTOM_NAME,
                        Component.literal("Оплатить (" + selection.size() + ")")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
                long totalPrice = 0, totalTax = 0;
                Map<String, Integer> itemCounts = new LinkedHashMap<>();
                for (int id : selection.getSelected()) {
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
                pay.set(DataComponents.LORE, new ItemLore(lorePay));
                container.setItem(navRowStart + 6, pay);
            } else {
                container.setItem(navRowStart + 6, ItemStack.EMPTY);
            }

            ItemStack balance = ShopUi.createBalanceItem(viewer);
            container.setItem(navRowStart, balance);
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Страница " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        private boolean selectedContainsAny(List<ShopListing> listings) {
            for (ShopListing l : listings) if (selection.isSelected(l.id)) return true;
            return false;
        }

        private Comparator<Map.Entry<ItemKey, List<ShopListing>>> getGroupComparator() {
            return switch (currentSort) {
                case PRICE_ASC -> Comparator.comparingLong(e -> e.getValue().stream().mapToLong(l -> l.price).min().orElse(Long.MAX_VALUE));
                case PRICE_DESC -> (a, b) -> Long.compare(
                        b.getValue().stream().mapToLong(l -> l.price).min().orElse(Long.MAX_VALUE),
                        a.getValue().stream().mapToLong(l -> l.price).min().orElse(Long.MAX_VALUE));
                case NEWEST -> (a, b) -> Long.compare(b.getValue().stream().mapToLong(l -> l.id).max().orElse(0),
                        a.getValue().stream().mapToLong(l -> l.id).max().orElse(0));
                case OLDEST -> (a, b) -> Long.compare(a.getValue().stream().mapToLong(l -> l.id).min().orElse(0),
                        b.getValue().stream().mapToLong(l -> l.id).min().orElse(0));
                case QUANTITY_ASC -> Comparator.comparingInt(e -> e.getValue().stream().mapToInt(l -> l.item.getCount()).sum());
                case QUANTITY_DESC -> (a, b) -> Integer.compare(
                        b.getValue().stream().mapToInt(l -> l.item.getCount()).sum(),
                        a.getValue().stream().mapToInt(l -> l.item.getCount()).sum());
            };
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < 45) {
                    List<ShopListing> group = slotToGroup.get(slot);
                    if (group != null) {
                        if (group.size() == 1) {
                            openConfirm(viewer, shop, group.get(0));
                            return;
                        }
                        if (selectionMode) {
                            boolean anySelected = selectedContainsAny(group);
                            if (anySelected) {
                                group.forEach(l -> selection.remove(l.id));
                            } else {
                                group.forEach(l -> selection.add(l.id));
                            }
                            updatePage();
                        } else {
                            openGroupDetail(viewer, shop, group, selection, this::updatePage);
                        }
                        return;
                    }
                }
                // Навигация
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * 45 < groupedEntries.size()) { page++; updatePage(); return; }
                if (slot == navRowStart + 2) {
                    selectionMode = !selectionMode;
                    if (!selectionMode) selection.clear();
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 1) {
                    OrdersUi.openClaims(viewer, EconomyCraft.getManager(viewer.level().getServer()));
                    return;
                }
                if (slot == navRowStart + 7) {
                    boolean shift = player.isShiftKeyDown();
                    if (shift) currentSort = currentSort.prev();
                    else currentSort = currentSort.next();
                    PlayerPrefs prefs = getPrefs(viewer);
                    prefs.sortType = currentSort;
                    savePrefs(viewer, prefs);
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 6 && selectionMode && !selection.isEmpty()) {
                    openMassConfirm(viewer, shop, selection.getSelected());
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public void removed(Player player) { shop.removeListener(listener); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }


    private static void openGroupDetail(ServerPlayer player, ShopManager shop, List<ShopListing> listings,
                                        SelectionManager selection, Runnable onUpdate) {
        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() {
                if (listings.isEmpty()) return Component.literal("Товары");
                return listings.get(0).item.getHoverName().copy().withStyle(ChatFormatting.GOLD);
            }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new GroupDetailMenu(id, inv, shop, listings, player, selection, onUpdate);
            }
        });
    }

    private static class GroupDetailMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final List<ShopListing> listings;
        private final ServerPlayer viewer;
        private final SimpleContainer container;
        private int page;
        private final int itemsPerPage = 45;
        private final int navRowStart;
        private final SelectionManager selection;
        private final Runnable onUpdate;
        private SortType currentSort;

        GroupDetailMenu(int id, Inventory inv, ShopManager shop, List<ShopListing> listings, ServerPlayer viewer,
                        SelectionManager selection, Runnable onUpdate) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.listings = listings;
            this.viewer = viewer;
            this.container = new SimpleContainer(54);
            this.navRowStart = 45;
            this.selection = selection;
            this.onUpdate = onUpdate;
            this.currentSort = getPrefs(viewer).sortType;
            setupSlots(inv);
            updatePage();
        }

        private void setupSlots(Inventory inv) {
            for (int i = 0; i < 54; i++) {
                int r = i / 9, c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
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
            List<ShopListing> sortedListings = new ArrayList<>(listings);
            Comparator<ShopListing> comp = getListingComparator();
            if (comp != null) sortedListings.sort(comp);

            container.clearContent();
            int start = page * itemsPerPage;
            int totalPages = (int) Math.ceil(sortedListings.size() / (double) itemsPerPage);

            for (int i = 0; i < itemsPerPage; i++) {
                int idx = start + i;
                if (idx >= sortedListings.size()) break;
                ShopListing l = sortedListings.get(idx);
                ItemStack display = l.item.copy();

                String sellerName;
                ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(l.seller);
                if (sellerPlayer != null) sellerName = IdentityCompat.of(sellerPlayer).name();
                else sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(l.seller);

                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                List<Component> lore = new ArrayList<>();
                lore.add(ShopUi.createPriceLore(l.price, tax));
                lore.add(ShopUi.labeledValue("Продавец", sellerName, ShopUi.LABEL_SECONDARY_COLOR));
                if (selection.isSelected(l.id)) {
                    lore.add(Component.literal("✓ ВЫДЕЛЕН").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                }
                display.set(DataComponents.LORE, new ItemLore(lore));
                if (selection.isSelected(l.id)) {
                    display.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
                container.setItem(i, display);
            }

            // Навигация
            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Предыдущая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }
            if (start + itemsPerPage < sortedListings.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Следующая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }

            // Кнопка сортировки
            ItemStack sortButton = new ItemStack(Items.COMPASS);
            String sortLabel = switch (currentSort) {
                case PRICE_ASC -> "Цена ↑";
                case PRICE_DESC -> "Цена ↓";
                case NEWEST -> "Новые ↓";
                case OLDEST -> "Старые ↑";
                case QUANTITY_ASC -> "Кол-во ↑";
                case QUANTITY_DESC -> "Кол-во ↓";
            };
            sortButton.set(DataComponents.CUSTOM_NAME,
                    Component.literal("Сортировка: " + sortLabel).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
            container.setItem(navRowStart + 7, sortButton);

            // Кнопка оплаты выделенного в этом меню
            if (!selection.isEmpty()) {
                ItemStack pay = new ItemStack(Items.ANVIL);
                pay.set(DataComponents.CUSTOM_NAME,
                        Component.literal("Оплатить (" + selection.size() + ")")
                                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
                long totalPrice = 0, totalTax = 0;
                Map<String, Integer> itemCounts = new LinkedHashMap<>();
                for (int sid : selection.getSelected()) {
                    ShopListing l = shop.getListing(sid);
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
                pay.set(DataComponents.LORE, new ItemLore(lorePay));
                container.setItem(navRowStart + 6, pay);
            } else {
                container.setItem(navRowStart + 6, ItemStack.EMPTY);
            }

            ItemStack back = new ItemStack(Items.BARRIER);
            back.set(DataComponents.CUSTOM_NAME, Component.literal("Назад").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.DARK_RED)));
            container.setItem(navRowStart + 8, back);

            ItemStack balance = ShopUi.createBalanceItem(viewer);
            container.setItem(navRowStart, balance);
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Страница " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        private Comparator<ShopListing> getListingComparator() {
            return switch (currentSort) {
                case PRICE_ASC -> Comparator.comparingLong(l -> l.price);
                case PRICE_DESC -> (a, b) -> Long.compare(b.price, a.price);
                case NEWEST -> (a, b) -> Integer.compare(b.id, a.id);
                case OLDEST -> (a, b) -> Integer.compare(a.id, b.id);
                case QUANTITY_ASC -> Comparator.comparingInt(l -> l.item.getCount());
                case QUANTITY_DESC -> (a, b) -> Integer.compare(b.item.getCount(), a.item.getCount());
            };
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < itemsPerPage) {
                    List<ShopListing> sorted = new ArrayList<>(listings);
                    Comparator<ShopListing> comp = getListingComparator();
                    if (comp != null) sorted.sort(comp);
                    int index = page * itemsPerPage + slot;
                    if (index < sorted.size()) {
                        ShopListing listing = sorted.get(index);
                        selection.toggle(listing.id);
                        updatePage();
                        if (onUpdate != null) onUpdate.run();
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * itemsPerPage < listings.size()) { page++; updatePage(); return; }
                if (slot == navRowStart + 7) {
                    boolean shift = player.isShiftKeyDown();
                    if (shift) currentSort = currentSort.prev();
                    else currentSort = currentSort.next();
                    PlayerPrefs prefs = getPrefs(viewer);
                    prefs.sortType = currentSort;
                    savePrefs(viewer, prefs);
                    updatePage();
                    return;
                }
                if (slot == navRowStart + 6 && !selection.isEmpty()) {
                    openMassConfirm(viewer, shop, selection.getSelected());
                    return;
                }
                if (slot == navRowStart + 8) {
                    player.closeContainer();
                    ShopUi.open(viewer, shop);
                    return;
                }
            }
            super.clicked(slot, dragType, type, player);
        }

        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }


    public static class ShopPlayerMenu extends AbstractContainerMenu {
        private final ShopManager shop;
        private final ServerPlayer viewer;
        private List<ShopListing> listings = new ArrayList<>();
        private final SimpleContainer container = new SimpleContainer(54);
        private int page;
        private final int navRowStart = 45;
        private final String target;
        private final Runnable listener = this::updatePage;

        public ShopPlayerMenu(int id, Inventory inv, ShopManager shop, ServerPlayer viewer, String target) {
            super(MenuType.GENERIC_9x6, id);
            this.shop = shop;
            this.viewer = viewer;
            this.target = target;
            updatePage();
            shop.addListener(listener);
            for (int i = 0; i < 54; i++) {
                int r = i / 9, c = i % 9;
                this.addSlot(new Slot(container, i, 8 + c * 18, 18 + r * 18) {
                    @Override public boolean mayPickup(Player p) { return false; }
                    @Override public boolean mayPlace(ItemStack s) { return false; }
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
                if (sellerPlayer != null) sellerName = IdentityCompat.of(sellerPlayer).name();
                else sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(l.seller);
                long tax = Math.round(l.price * EconomyConfig.get().taxRate);
                display.set(DataComponents.LORE, new ItemLore(List.of(
                        ShopUi.createPriceLore(l.price, tax),
                        ShopUi.labeledValue("Продавец", sellerName, ShopUi.LABEL_SECONDARY_COLOR))));
                container.setItem(i, display);
            }
            if (page > 0) {
                ItemStack prev = new ItemStack(Items.ARROW);
                prev.set(DataComponents.CUSTOM_NAME, Component.literal("Предыдущая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 3, prev);
            }
            if (start + 45 < listings.size()) {
                ItemStack next = new ItemStack(Items.ARROW);
                next.set(DataComponents.CUSTOM_NAME, Component.literal("Следующая страница").withStyle(s -> s.withItalic(false)));
                container.setItem(navRowStart + 5, next);
            }
            ItemStack balance = ShopUi.createBalanceItem(viewer);
            container.setItem(navRowStart, balance);
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("Страница " + (page + 1) + "/" + Math.max(1, totalPages)).withStyle(s -> s.withItalic(false)));
            container.setItem(navRowStart + 4, paper);
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot < 45) {
                    int index = page * 45 + slot;
                    if (index < listings.size()) {
                        ShopListing listing = listings.get(index);
                        if (listing.seller.equals(player.getUUID())) openRemove((ServerPlayer) player, shop, listing);
                        else openConfirm((ServerPlayer) player, shop, listing);
                        return;
                    }
                }
                if (slot == navRowStart + 3 && page > 0) { page--; updatePage(); return; }
                if (slot == navRowStart + 5 && (page + 1) * 45 < listings.size()) { page++; updatePage(); return; }
            }
            super.clicked(slot, dragType, type, player);
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void removed(Player player) { shop.removeListener(listener); }
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
            confirm.set(DataComponents.CUSTOM_NAME, Component.literal("Подтвердить").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            String sellerName;
            ServerPlayer sellerPlayer = viewer.level().getServer().getPlayerList().getPlayer(listing.seller);
            if (sellerPlayer != null) sellerName = IdentityCompat.of(sellerPlayer).name();
            else sellerName = EconomyCraft.getManager(viewer.level().getServer()).getBestName(listing.seller);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(DataComponents.LORE, new ItemLore(List.of(
                    ShopUi.createPriceLore(listing.price, tax),
                    ShopUi.labeledValue("Продавец", sellerName, ShopUi.LABEL_SECONDARY_COLOR))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(DataComponents.CUSTOM_NAME, Component.literal("Отмена").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                @Override public boolean mayPickup(Player p) { return false; }
            });
            int y = 40;
            for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
            for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
        }

        @Override
        public void clicked(int slot, int dragType, ClickType type, Player player) {
            if (type == ClickType.PICKUP) {
                if (slot == 2) {
                    ShopListing current = shop.getListing(listing.id);
                    ServerPlayer sp = (ServerPlayer) player;
                    var server = sp.level().getServer();
                    if (current == null) sp.sendSystemMessage(Component.literal("Объявление больше не доступно").withStyle(ChatFormatting.RED));
                    else {
                        EconomyManager eco = EconomyCraft.getManager(server);
                        long cost = current.price, tax = Math.round(cost * EconomyConfig.get().taxRate), total = cost + tax;
                        if (eco.getBalance(player.getUUID(), true) < total) sp.sendSystemMessage(Component.literal("Недостаточно средств").withStyle(ChatFormatting.RED));
                        else {
                            eco.removeMoney(player.getUUID(), total);
                            eco.addMoney(current.seller, cost);
                            shop.removeListing(current.id);
                            shop.notifySellerSale(current, sp);
                            ItemStack stack = current.item.copy();
                            int count = stack.getCount();
                            Component name = stack.getHoverName();
                            String sellerName = server.getPlayerList().getPlayer(current.seller) != null ?
                                    IdentityCompat.of(server.getPlayerList().getPlayer(current.seller)).name() :
                                    eco.getBestName(current.seller);
                            if (!player.getInventory().add(stack)) {
                                if (!stack.isEmpty()) {
                                    shop.addDelivery(player.getUUID(), stack);
                                    ClickEvent ev = ChatCompat.runCommandEvent("/eco orders claim");
                                    if (ev != null) sp.sendSystemMessage(Component.literal("Предмет сохранён в доставке: ").withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal("[Забрать]").withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev))));
                                    else ChatCompat.sendRunCommandTellraw(sp, "Предмет сохранён в доставке: ", "[Забрать]", "/eco orders claim");
                                }
                            } else sp.sendSystemMessage(Component.literal("Куплено " + count + "x " + name.getString() + " у " + sellerName +
                                    " за " + EconomyCraft.formatMoney(total)).withStyle(ChatFormatting.GREEN));
                        }
                    }
                    player.closeContainer();
                    ShopUi.open(sp, shop);
                    return;
                }
                if (slot == 6) { player.closeContainer(); ShopUi.open((ServerPlayer) player, shop); return; }
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
            // аналогично, код не меняется
            ItemStack confirm = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            confirm.set(DataComponents.CUSTOM_NAME, Component.literal("Подтвердить").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.GREEN)));
            container.setItem(2, confirm);

            ItemStack item = listing.item.copy();
            long tax = Math.round(listing.price * EconomyConfig.get().taxRate);
            item.set(DataComponents.LORE, new ItemLore(List.of(
                    ShopUi.createPriceLore(listing.price, tax),
                    ShopUi.labeledValue("Продавец", "вы", ShopUi.LABEL_SECONDARY_COLOR),
                    Component.literal("Это снимет объявление").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED)))));
            container.setItem(4, item);

            ItemStack cancel = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            cancel.set(DataComponents.CUSTOM_NAME, Component.literal("Отмена").withStyle(s -> s.withItalic(false).withBold(true).withColor(ChatFormatting.DARK_RED)));
            container.setItem(6, cancel);

            for (int i = 0; i < 9; i++) this.addSlot(new Slot(container, i, 8 + i * 18, 20) {
                @Override public boolean mayPickup(Player p) { return false; }
            });
            int y = 40;
            for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
            for (int c = 0; c < 9; c++) this.addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
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
                                    Component msg = Component.literal("Предмет сохранён в доставке: ").withStyle(ChatFormatting.YELLOW)
                                            .append(Component.literal("[Забрать]").withStyle(s -> s.withUnderlined(true).withColor(ChatFormatting.GREEN).withClickEvent(ev)));
                                    ((ServerPlayer) player).sendSystemMessage(msg);
                                } else ChatCompat.sendRunCommandTellraw((ServerPlayer) player, "Предмет сохранён в доставке: ", "[Забрать]", "/eco orders claim");
                            }
                        } else viewer.sendSystemMessage(Component.literal("Объявление снято"));
                    } else viewer.sendSystemMessage(Component.literal("Объявление больше не доступно"));
                    player.closeContainer();
                    ShopUi.open((ServerPlayer) player, shop);
                    return;
                }
                if (slot == 6) { player.closeContainer(); ShopUi.open((ServerPlayer) player, shop); return; }
            }
            super.clicked(slot, dragType, type, player);
        }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int idx) { return ItemStack.EMPTY; }
    }
}