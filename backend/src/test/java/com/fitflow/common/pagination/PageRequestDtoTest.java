package com.fitflow.common.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRequestDtoTest {

    @Test
    @DisplayName("Default pagination values are page=0 and size=20")
    void testDefaults() {
        PageRequestDto dto = new PageRequestDto(null, null, null);
        assertEquals(0, dto.page());
        assertEquals(20, dto.size());
    }

    @Test
    @DisplayName("Page size is capped at 100 when a larger size is requested")
    void testSizeCappedAt100() {
        PageRequestDto dto = new PageRequestDto(0, 500, null);
        assertEquals(100, dto.size());
    }

    @Test
    @DisplayName("Negative page or size are normalized to defaults")
    void testNegativeNormalization() {
        PageRequestDto dto = new PageRequestDto(-5, -10, null);
        assertEquals(0, dto.page());
        assertEquals(20, dto.size());
    }

    @Test
    @DisplayName("toPageable applies whitelisted sort fields and falls back for unapproved fields")
    void testSortWhitelisting() {
        Set<String> whitelisted = Set.of("name", "createdAt", "startsAt");

        // Approved field
        PageRequestDto validSortDto = new PageRequestDto(1, 15, "startsAt,desc");
        Pageable p1 = validSortDto.toPageable(whitelisted, "createdAt", Sort.Direction.DESC);
        assertEquals(1, p1.getPageNumber());
        assertEquals(15, p1.getPageSize());
        assertTrue(p1.getSort().getOrderFor("startsAt").isDescending());

        // Unapproved / injection attempt field falls back to defaultSortField
        PageRequestDto maliciousSortDto = new PageRequestDto(0, 10, "passwordHash,asc");
        Pageable p2 = maliciousSortDto.toPageable(whitelisted, "createdAt", Sort.Direction.DESC);
        assertEquals("createdAt", p2.getSort().iterator().next().getProperty());
    }
}
