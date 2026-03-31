package com.example.multiapp.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PageNormalizer {
    private PageNormalizer() {}
//    private static final Sort DEFAULT_SORT =
//            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
//    private static final Set<String> ALLOWED_SORTS = Set.of("updatedAt", "id", "name");
    public static Pageable normalize(Pageable in, int maxSize, int defaultSize,
                                     Sort defaultSort, Set<String> allowedSorts) {
        int page = Math.max(0, in.getPageNumber());
        int size = in.getPageSize();
        if (size < 1) size = defaultSize;
        if (size > maxSize) size = maxSize;
        Sort sort = in.getSort().isSorted() ? whitelist(in.getSort(), allowedSorts) : defaultSort;
        if(!sort.isSorted()) sort = defaultSort;
        return PageRequest.of(page, size, sort);
    }

    private static Sort whitelist(Sort in, Set<String> allowed) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order o : in) {
            if(!allowed.contains(o.getProperty())) continue;
            orders.add(new Sort.Order(o.getDirection(), o.getProperty()));
        }
        return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
    }
}
