package com.electricwealth.api.repository;

import java.util.List;

/** One page of rows plus the total number of rows across all pages. */
public record PageResult<T>(List<T> items, long total) {
}
