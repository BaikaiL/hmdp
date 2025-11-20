package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.Key;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Resource
	private IUserService userService;

	@Override
	public Result follow(Long followedUserId, Boolean isFollow) {
		Long userId = UserHolder.getUser().getId();
		String key = "follows:" + userId;
		// 判断是否已经关注
		if(BooleanUtil.isFalse(isFollow)){
			Follow follow = new Follow();
			follow.setFollowUserId(followedUserId);
			follow.setUserId(userId);
			boolean isSuccess = save(follow);

			// 关注成功，添加到redis的关注集合中
			if(isSuccess){
				stringRedisTemplate.opsForSet().add(key, followedUserId.toString());
			}
		}else{
			// 已经关注，取关
			boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followedUserId));
			// 取关成功，从redis集合中删除用户
			if(isSuccess){
				stringRedisTemplate.opsForSet().remove(key, followedUserId.toString());
			}
		}

		return Result.ok();
	}

	@Override
	public Result isFollow(Long followedUserId) {
		Long userId = UserHolder.getUser().getId();
		Integer count = query().eq("user_id", userId).eq("follow_user_id", followedUserId).count();
		return Result.ok(count > 0);
	}

	@Override
	public Result commonFollows(Long id) {
		// 查redis中两个集合的交集
		String key1 = "follows:" + id;
		String key2 = "follows:" + UserHolder.getUser().getId();

		Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
		if(intersect == null || intersect.isEmpty()){
			return Result.ok(Collections.EMPTY_LIST);
		}
		// 解析出用户信息
		List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
		List<UserDTO> userDTOS = userService.listByIds(collect).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

		return Result.ok(userDTOS);
	}
}
