package com.example.multiapp.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total
) {
    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(
                p.getContent(),
                p.getNumber(), // 当前页号
                p.getSize(), // 每页大小
                p.getTotalElements() // 总元素数
        );
    }
}
