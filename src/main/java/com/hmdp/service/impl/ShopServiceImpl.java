package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.ErrorConstants.DATA_DO_NOT_EXIST;
import static com.hmdp.utils.RedisConstants.*;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public Result queryById(Long id) {

		// 从redis缓存中取
		String key = CACHE_SHOP_KEY + id.toString();
		String shopJson = stringRedisTemplate.opsForValue().get(key);

		// 存在（shopJson不为空且不为占位符），直接返回
		if (StringUtils.isNotBlank(shopJson) && !EMPTY_PLACEHOLDER.equals(shopJson)) {
			Shop shop = JSONUtil.toBean(shopJson, Shop.class);
			return Result.ok(shop);
		}

		// 缓存的值为占位符，则说明该数据在数据库中不存在，直接返回错误信息
		if(EMPTY_PLACEHOLDER.equals(shopJson)){
			return Result.fail(DATA_DO_NOT_EXIST);
		}

		// 如果缓存中不存在，到数据库里查
		Shop shop = getById(id);

		if (shop == null){
			// 解决缓存穿透问题，缓存占位符
			stringRedisTemplate.opsForValue().set(key,EMPTY_PLACEHOLDER,CACHE_NULL_TTL,TimeUnit.MINUTES);
			return Result.fail(DATA_DO_NOT_EXIST);
		}

		// 将shop添加到缓存中
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

		return Result.ok(shop);
	}

	@Override
	public Result updateShop(Shop shop) {
		// 先更新数据库
		updateById(shop);
		// 再删除缓存
		stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
		return Result.ok();
	}
}
