package com.rance.galcommunity.community.web;

import com.rance.galcommunity.community.service.CategoryQueryService;
import com.rance.galcommunity.community.web.dto.CategoryNode;
import com.rance.galcommunity.shared.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryQueryService categories;

    public CategoryController(CategoryQueryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public ApiResponse<List<CategoryNode>> tree() {
        return ApiResponse.ok(categories.tree());
    }
}
