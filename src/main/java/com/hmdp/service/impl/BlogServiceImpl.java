package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

	@Resource
	private IUserService userService;

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Resource
	private IFollowService followService;
	
	@Override
	public Result queryBlogById(Long id) {
		
		// 查博客
		Blog blog = getById(id);
		
		if(blog == null){
			return Result.fail("笔记不存在");
		}
		
		// 设置用户信息
		setBlogUser(blog);
		// 设置是否点赞
		isBlogLiked(blog);
		return Result.ok(blog);
	}

	private void isBlogLiked(Blog blog) {
		UserDTO userDTO = UserHolder.getUser();
		if(userDTO == null){
			return;
		}
		// 获取用户
		Long userId = userDTO.getId();
		// 判断用户是否点过赞
		String key = BLOG_LIKED_KEY + blog.getId();
		Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
		blog.setIsLike(score != null);
	}

	@Override
	public Result queryHotBlog(Integer current) {
		// 根据用户查询
		Page<Blog> page = query()
				.orderByDesc("liked")
				.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
		// 获取当前页数据
		List<Blog> records = page.getRecords();
		// 查询用户
		records.forEach(blog -> {
			setBlogUser(blog);
			isBlogLiked(blog);
		});
		return Result.ok(records);
	}

	@Override
	public Result likeBlog(Long id) {

		// 获取用户
		Long userId = UserHolder.getUser().getId();
		// 判断用户是否点过赞
		String key = BLOG_LIKED_KEY + id;
		Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
		// 未点过，点赞数++，添加到set集合
		if(score == null){
			boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
			if(isSuccess){
				stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
			}
		}
		else {
			// 点过，点赞数--，移除set集合
			boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
			if(isSuccess){
				stringRedisTemplate.opsForZSet().remove(key, userId.toString());
			}
		}
		return Result.ok();
	}

	@Override
	public Result queryBlogLikes(Long id) {
		String key = BLOG_LIKED_KEY + id;
		// 获取zset前5名的用户
		Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

		if(top5 == null){
			return Result.ok(Collections.EMPTY_LIST);
		}

		// 解析用户列表
		List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
		String strIds = StrUtil.join(",", ids);
		// 转为dto对象
		List<Object> userDTOs = userService.query()
				.in("id",ids).last("ORDER BY FILED(id," + strIds + ")").list()
				.stream()
				.map(user -> BeanUtil.copyProperties(user, UserDTO.class))
				.collect(Collectors.toList());
		// 返回前端
		return Result.ok(userDTOs);
	}

	@Override
	public Result saveBlog(Blog blog) {
		// 获取登录用户
		UserDTO user = UserHolder.getUser();
		blog.setUserId(user.getId());
		save(blog);

		// 推送笔记到所有粉丝的收件箱
		// 1. 获取粉丝列表
		Long userId = UserHolder.getUser().getId();
		List<Follow> followList = followService.query().eq("user_id", userId).list();

		// 2.推送到每一个粉丝的收件箱
		for(Follow follow : followList){
			String feedKey = "feed:" + follow.getUserId();
			stringRedisTemplate.opsForZSet().add(feedKey, blog.getId().toString(), System.currentTimeMillis());
		}


		return Result.ok(blog.getId());
	}

	private void setBlogUser(Blog blog) {
		Long userId = blog.getUserId();
		User user = userService.getById(userId);
		blog.setName(user.getNickName());
		blog.setIcon(user.getIcon());
	}
}
