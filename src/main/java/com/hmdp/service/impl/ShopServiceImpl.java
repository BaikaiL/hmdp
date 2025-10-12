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
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;


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

		// 存在，直接返回
		if (StringUtils.isNotBlank(shopJson)) {
			Shop shop = JSONUtil.toBean(shopJson, Shop.class);
			return Result.ok(shop);
		}

		// 没命中缓存，到数据库里查
		Shop shop = getById(id);

		if (shop == null){
			return Result.fail(DATA_DO_NOT_EXIST);
		}

		// 将shop添加到缓存中
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

		return Result.ok(shop);
	}
}
