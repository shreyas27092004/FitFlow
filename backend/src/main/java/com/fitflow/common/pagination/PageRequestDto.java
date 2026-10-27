package com.fitflow.common.pagination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public record PageRequestDto(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        String sort
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequestDto {
        page = (page == null || page < 0) ? DEFAULT_PAGE : page;
        size = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public Pageable toPageable(Set<String> whitelistedFields, String defaultSortField, Sort.Direction defaultDirection) {
        int targetPage = page != null ? page : DEFAULT_PAGE;
        int targetSize = size != null ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;

        if (sort == null || sort.isBlank() || whitelistedFields == null || whitelistedFields.isEmpty()) {
            return PageRequest.of(targetPage, targetSize, Sort.by(defaultDirection, defaultSortField));
        }

        String[] parts = sort.split(",");
        String requestedField = parts[0].trim();
        Sort.Direction direction = defaultDirection;

        if (parts.length > 1) {
            String dirStr = parts[1].trim();
            if ("desc".equalsIgnoreCase(dirStr)) {
                direction = Sort.Direction.DESC;
            } else if ("asc".equalsIgnoreCase(dirStr)) {
                direction = Sort.Direction.ASC;
            }
        }

        if (whitelistedFields.contains(requestedField)) {
            return PageRequest.of(targetPage, targetSize, Sort.by(direction, requestedField));
        }

        return PageRequest.of(targetPage, targetSize, Sort.by(defaultDirection, defaultSortField));
    }
}
