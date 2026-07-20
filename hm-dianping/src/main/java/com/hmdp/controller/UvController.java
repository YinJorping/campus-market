package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.UV_BLOG_KEY;

@RestController
@RequestMapping("/uv")
public class UvController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/blog/{blogId}")
    public Result recordBlogUv(@PathVariable Long blogId) {
        Long userId = UserHolder.getUser().getId();
        stringRedisTemplate.opsForHyperLogLog().add(UV_BLOG_KEY + blogId, userId.toString());
        return Result.ok();
    }

    @GetMapping("/blog/{blogId}")
    public Result queryBlogUv(@PathVariable Long blogId) {
        Long count = stringRedisTemplate.opsForHyperLogLog().size(UV_BLOG_KEY + blogId);
        return Result.ok(count);
    }
}
