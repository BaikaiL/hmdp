package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.Key;
import java.util.List;

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
		// 获取用户
		Long userId = UserHolder.getUser().getId();
		// 判断用户是否点过赞
		String key = BLOG_LIKED_KEY + blog.getId();
		Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
		blog.setIsLike(isLiked);
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
		Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
		// 未点过，点赞数++，添加到set集合
		if(BooleanUtil.isFalse(isLiked)){
			boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
			if(isSuccess){
				stringRedisTemplate.opsForSet().add(key, userId.toString());
			}
		}
		else {
			// 点过，点赞数--，移除set集合
			boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
			if(isSuccess){
				stringRedisTemplate.opsForSet().remove(key, userId.toString());
			}
		}
		return Result.ok();
	}

	private void setBlogUser(Blog blog) {
		Long userId = blog.getUserId();
		User user = userService.getById(userId);
		blog.setName(user.getNickName());
		blog.setIcon(user.getIcon());
	}
}
