package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Override
    @Transactional
    public Result saveComment(BlogComments comment) {
        String content = comment.getContent();
        if (StrUtil.isBlank(content)) {
            return Result.fail("评论内容不能为空");
        }
        Long userId = UserHolder.getUser().getId();
        comment.setUserId(userId);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setLiked(0);
        comment.setStatus(false);
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        if (comment.getAnswerId() == null) {
            comment.setAnswerId(0L);
        }
        save(comment);
        // 更新博客评论数
        blogService.update()
                .setSql("comments = comments + 1")
                .eq("id", comment.getBlogId()).update();
        return Result.ok();
    }

    @Override
    public Result queryCommentsByBlogId(Long blogId, Integer current) {
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .orderByAsc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<BlogComments> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(records);
        }
        // 收集所有用户id，批量查用户信息
        List<Long> userIds = records.stream()
                .map(BlogComments::getUserId)
                .distinct()
                .collect(Collectors.toList());
        List<User> users = userService.listByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>();
        for (User user : users) {
            userMap.put(user.getId(), BeanUtil.copyProperties(user, UserDTO.class));
        }
        // 把用户信息放到一个新的 list 中，每条评论带上用户昵称和头像
        List<Map<String, Object>> result = records.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("userId", c.getUserId());
            map.put("blogId", c.getBlogId());
            map.put("parentId", c.getParentId());
            map.put("answerId", c.getAnswerId());
            map.put("content", c.getContent());
            map.put("liked", c.getLiked());
            map.put("createTime", c.getCreateTime());
            UserDTO u = userMap.get(c.getUserId());
            if (u != null) {
                map.put("userName", u.getNickName());
                map.put("userIcon", u.getIcon());
            }
            return map;
        }).collect(Collectors.toList());
        return Result.ok(result);
    }

    @Override
    public Result updateComment(Long id, BlogComments comment) {
        BlogComments exist = getById(id);
        if (exist == null) {
            return Result.fail("评论不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!exist.getUserId().equals(userId)) {
            return Result.fail("无权修改他人评论");
        }
        if (StrUtil.isBlank(comment.getContent())) {
            return Result.fail("评论内容不能为空");
        }
        exist.setContent(comment.getContent());
        exist.setUpdateTime(LocalDateTime.now());
        updateById(exist);
        return Result.ok();
    }

    @Override
    public Result deleteComment(Long id) {
        BlogComments exist = getById(id);
        if (exist == null) {
            return Result.fail("评论不存在");
        }
        Long userId = UserHolder.getUser().getId();
        if (!exist.getUserId().equals(userId)) {
            return Result.fail("无权删除他人评论");
        }
        removeById(id);
        blogService.update()
                .setSql("comments = comments - 1")
                .eq("id", exist.getBlogId()).update();
        return Result.ok();
    }
}
