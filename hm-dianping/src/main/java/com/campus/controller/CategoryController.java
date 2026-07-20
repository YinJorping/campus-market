package com.campus.controller;

import com.hmdp.dto.Result;
import com.campus.service.ICategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Resource
    private ICategoryService categoryService;

    @GetMapping
    public Result list() {
        return Result.ok(categoryService.listRootCategories());
    }
}