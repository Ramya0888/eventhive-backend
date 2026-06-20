package com.eventhive.eventhive_backend.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic pagination envelope — decouples our API from Spring's Page type
 * and gives the client clean paging metadata.
 *
 * <T> content type; built from a Page<E> plus a mapper E -> T.
 */
@Getter
@Builder
public class PagedResponse<T> {
    private List<T> content;
    private int page;          // current page number (0-based)
    private int size;          // page size
    private long totalElements;
    private int totalPages;
    private boolean last;      // is this the final page?

    public static <E, T> PagedResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return PagedResponse.<T>builder()
                .content(source.getContent().stream().map(mapper).toList())
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages())
                .last(source.isLast())
                .build();
    }
}