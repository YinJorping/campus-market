package com.campus.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.entity.Category;
import com.campus.mapper.CategoryMapper;
import com.campus.service.ICategoryService;
import com.campus.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    @Override
    public List<CategoryVO> listRootCategories() {
        List<Category> categories = query()
                .eq("parent_id", 0)
                .orderByAsc("sort")
                .list();
        return categories.stream()
                .map(c -> BeanUtil.copyProperties(c, CategoryVO.class))
                .collect(Collectors.toList());
    }
}
