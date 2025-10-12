package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.ErrorConstants.DATA_DO_NOT_EXIST;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public Result queryTypeList() {

		// 尝试从redis缓存中找
		String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

		// 找到则直接返回
		if(StringUtils.isNotBlank(shopTypeJson)){
			// 将json转为List<shopType>
			List<ShopType> list = JSONUtil.toList(shopTypeJson, ShopType.class);
			return Result.ok(list);
		}

		// 没找到则在mysql数据库中查找
		List<ShopType> typeList = query().orderByAsc("sort").list();

		if(typeList == null){
			return Result.fail(DATA_DO_NOT_EXIST);
		}

		// 添加到redis缓存
		stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList),CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

		return Result.ok();
	}
}
