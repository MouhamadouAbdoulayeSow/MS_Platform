// Package: sn.lms.platform.shared.util

package edu.projet.MS_Platform.shared.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Map;

public class PageUtils {
    
    public static Pageable of(int page, int size, String sortBy, String dir) {
        int actualPage = Math.max(0, page);
        int actualSize = Math.min(100, Math.max(1, size));
        String actualSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy : "createdAt";
        Sort.Direction direction = ("asc".equalsIgnoreCase(dir)) ? Sort.Direction.ASC : Sort.Direction.DESC;
        
        return PageRequest.of(actualPage, actualSize, Sort.by(direction, actualSortBy));
    }
    
    public static <T> Map<String, Object> toMap(Page<T> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("content", page.getContent());
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        result.put("page", page.getNumber());
        result.put("size", page.getSize());
        result.put("last", page.isLast());
        result.put("first", page.isFirst());
        result.put("empty", page.isEmpty());
        return result;
    }
}
