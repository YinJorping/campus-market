package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    @PostMapping
    public Result saveComment(@RequestBody BlogComments comment) {
        return blogCommentsService.saveComment(comment);
    }

    @GetMapping("/{blogId}")
    public Result queryCommentsByBlogId(@PathVariable Long blogId,
                                        @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryCommentsByBlogId(blogId, current);
    }

    @PutMapping("/{id}")
    public Result updateComment(@PathVariable Long id, @RequestBody BlogComments comment) {
        return blogCommentsService.updateComment(id, comment);
    }

    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable Long id) {
        return blogCommentsService.deleteComment(id);
    }
}
