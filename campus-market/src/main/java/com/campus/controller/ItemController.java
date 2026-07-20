package com.campus.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.dto.ItemQueryDTO;
import com.campus.dto.SaveItemDTO;
import com.campus.dto.UpdateItemDTO;
import com.campus.service.IItemService;
import com.campus.vo.ItemDetailVO;
import com.campus.vo.ItemListVO;
import com.campus.vo.ItemNearbyVO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    @Resource
    private IItemService itemService;

    @GetMapping("/nearby")
    public Result nearbyItems(@RequestParam Double x,
                              @RequestParam Double y,
                              @RequestParam(defaultValue = "5000") Integer radius,
                              @RequestParam(defaultValue = "20") Integer limit) {
        if (radius > 20000) {
            radius = 20000;
        }
        if (limit > 50) {
            limit = 50;
        }
        List<ItemNearbyVO> list = itemService.nearbyItems(x, y, radius, limit);
        return Result.ok(list);
    }

    @GetMapping
    public Result listItems(ItemQueryDTO query) {
        Page<ItemListVO> page = itemService.listItems(query);
        return Result.ok(page);
    }

    @GetMapping("/{id}")
    public Result getItemDetail(@PathVariable Long id) {
        ItemDetailVO vo = itemService.getItemDetail(id);
        if (vo == null) {
            return Result.fail("商品不存在");
        }
        return Result.ok(vo);
    }

    @PostMapping
    public Result createItem(@Valid @RequestBody SaveItemDTO dto) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return itemService.createItem(dto, user.getId());
    }

    @PutMapping("/{id}")
    public Result updateItem(@PathVariable Long id, @RequestBody UpdateItemDTO dto) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return itemService.updateItem(id, user.getId(), dto);
    }

    @DeleteMapping("/{id}")
    public Result deleteItem(@PathVariable Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        return itemService.deleteItem(id, user.getId());
    }
}