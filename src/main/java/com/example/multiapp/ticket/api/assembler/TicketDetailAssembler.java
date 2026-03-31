package com.example.multiapp.ticket.api.assembler;

import com.example.multiapp.common.api.dto.SliceBlock;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

public class TicketDetailAssembler {
    public static <T> SliceBlock<T> sliceBlock(
            IntFunction<List<T>> listFn, // 传size+1进去
            LongSupplier countFn,
            int size
    ) {
        List<T> itemsPlusOne = listFn.apply(size + 1);
        boolean hasMore = itemsPlusOne.size() > size;
        List<T> items = hasMore ? itemsPlusOne.subList(0, size) : itemsPlusOne;
        long total = countFn.getAsLong();
        return new SliceBlock<>(items, total, hasMore);
    }
}
