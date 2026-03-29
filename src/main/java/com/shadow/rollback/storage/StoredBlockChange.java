package com.shadow.rollback.storage;

import com.shadow.rollback.model.BlockChangeRecord;

public record StoredBlockChange(long id, BlockChangeRecord record) {
}
