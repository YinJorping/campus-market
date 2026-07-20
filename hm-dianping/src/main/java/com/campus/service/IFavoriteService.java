package com.campus.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.entity.Favorite;
import com.campus.vo.ItemListVO;
import com.hmdp.dto.Result;

public interface IFavoriteService extends IService<Favorite> {

    Result favorite(Long itemId);

    Result unfavorite(Long itemId);

    Page<ItemListVO> pageFavorites(int current, int pageSize);
}
