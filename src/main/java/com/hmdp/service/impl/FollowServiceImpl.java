package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

	@Override
	public Result follow(Long followedUserId, Boolean isFollow) {
		Long userId = UserHolder.getUser().getId();
		// 判断是否已经关注
		if(BooleanUtil.isFalse(isFollow)){
			Follow follow = new Follow();
			follow.setFollowUserId(followedUserId);
			follow.setUserId(userId);
			save(follow);
		}else{
			// 已经关注，取关
			remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followedUserId));
		}

		return Result.ok();
	}

	@Override
	public Result isFollow(Long followedUserId) {
		Long userId = UserHolder.getUser().getId();
		Integer count = query().eq("user_id", userId).eq("follow_user_id", followedUserId).count();
		return Result.ok(count > 0);
	}
}
