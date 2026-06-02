package com.reazip.economycraft.shop;

import java.util.HashSet;
import java.util.Set;

public class SelectionManager {
    private final Set<Integer> selectedIds = new HashSet<>();

    public boolean isSelected(int id) {
        return selectedIds.contains(id);
    }

    public void toggle(int id) {
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);
    }

    public void add(int id) {
        selectedIds.add(id);
    }

    public void remove(int id) {
        selectedIds.remove(id);
    }

    public void clear() {
        selectedIds.clear();
    }

    public Set<Integer> getSelected() {
        return new HashSet<>(selectedIds);
    }

    public boolean isEmpty() {
        return selectedIds.isEmpty();
    }

    public int size() {
        return selectedIds.size();
    }
}