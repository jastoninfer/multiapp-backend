package com.example.multiapp.common.api.dto;

import java.util.List;

public record SliceBlock<T>(
        List<T> items,
        long totalCount,
        boolean hasMore
) {
}
