package com.campus.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.service.IFavoriteService;
import com.campus.vo.ItemListVO;
import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api")
public class FavoriteController {

    @Resource
    private IFavoriteService favoriteService;

    @PostMapping("/items/{itemId}/favorite")
    public Result favorite(@PathVariable Long itemId) {
        return favoriteService.favorite(itemId);
    }

    @DeleteMapping("/items/{itemId}/favorite")
    public Result unfavorite(@PathVariable Long itemId) {
        return favoriteService.unfavorite(itemId);
    }

    @GetMapping("/users/me/favorites")
    public Result pageFavorites(@RequestParam(defaultValue = "1") Integer current,
                                @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<ItemListVO> page = favoriteService.pageFavorites(current, pageSize);
        return Result.ok(page);
    }
}
