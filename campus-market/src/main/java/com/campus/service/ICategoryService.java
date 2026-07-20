package com.campus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.entity.Category;
import com.campus.vo.CategoryVO;

import java.util.List;

public interface ICategoryService extends IService<Category> {

    /**
     * 获取一级分类列表，按 sort 升序排列
     */
    List<CategoryVO> listRootCategories();
}
