package com.shadow.rollback.storage;

import com.shadow.rollback.model.EntitySnapshot;

public record StoredEntitySnapshot(long id, EntitySnapshot snapshot) {
}
