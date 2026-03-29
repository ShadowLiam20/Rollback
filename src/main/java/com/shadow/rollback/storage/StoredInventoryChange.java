package com.shadow.rollback.storage;

import com.shadow.rollback.model.InventoryChangeRecord;

public record StoredInventoryChange(long id, InventoryChangeRecord record) {
}
