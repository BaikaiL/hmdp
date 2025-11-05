package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

	@Autowired
	private CacheClient cacheClient;



	@Override
	public Result queryById(Long id) {

		// 防止缓存穿透
//		Shop shop = cacheClient.queryWithThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

		// 用逻辑过期解决击穿
		Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, LOCK_SHOP_KEY, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

//		Shop shop = queryWithMutex(id);
		if(shop == null){
			return Result.fail(DATA_DO_NOT_EXIST);
		}
		return Result.ok(shop);
	}

	// 防止缓存击穿的查询方法，逻辑过期实现
//	public Shop queryWithLogicalExpire(Long id) {
//
//		// 从redis缓存中取
//		String key = CACHE_SHOP_KEY + id.toString();
//		String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//		// 不存在存在（shopJson为空），直接返回
//		if (StringUtils.isNotBlank(shopJson)) {
//			return null;
//		}
//
//		// 存在，判断过期时间
//		//反序列化
//		RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//		Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//		LocalDateTime expire = redisData.getExpireTime();
//
//		if(expire.isAfter(LocalDateTime.now())) {
//			// 未过期，直接返回
//			return shop;
//		}
//		// 过期，缓存重建
//		// 获取锁
//		String lockKey = LOCK_SHOP_KEY + id;
//		boolean isLock = tryLock(lockKey);
//
//		// 获取锁成功
//		if(isLock){
//			try {
//				EXECUTOR_SERVICE.submit(()->{
//					this.saveData2Redis(id,1800L);
//				});
//			} catch (Exception e) {
//				throw new RuntimeException(e);
//			} finally {
//				unLock(lockKey);
//			}
//		}
//
//		// 获取锁失败，返回旧数据
//		return shop;
//	}

	// 防止缓存击穿的查询方法，互斥锁实现
	public Shop queryWithMutex(Long id) {

		// 从redis缓存中取
		String key = CACHE_SHOP_KEY + id.toString();
		String shopJson = stringRedisTemplate.opsForValue().get(key);

		// 存在（shopJson不为空且不为占位符），直接返回
		if (StringUtils.isNotBlank(shopJson) && !EMPTY_PLACEHOLDER.equals(shopJson)) {
			return JSONUtil.toBean(shopJson, Shop.class);
		}

		// 缓存的值为占位符，则说明该数据在数据库中不存在，直接返回错误信息
		if(EMPTY_PLACEHOLDER.equals(shopJson)){
			return null;
		}

		// 尝试获取锁
		Shop shop = null;
		String lockKey = LOCK_SHOP_KEY + id;
		try {

			boolean isLock = tryLock(lockKey);

			// 获取锁失败，等待
			if(!isLock){
				Thread.sleep(10);
				return queryWithMutex(id);
			}

			// 锁获取成功，就正常从数据库中重建缓存

			// 如果缓存中不存在，到数据库里查
			shop = getById(id);

			if (shop == null){
				// 解决缓存穿透问题，缓存占位符
				stringRedisTemplate.opsForValue().set(key,EMPTY_PLACEHOLDER,CACHE_NULL_TTL,TimeUnit.MINUTES);
				return null;
			}

			// 将shop添加到缓存中
			stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}finally {
			unLock(lockKey);
		}

		return shop;
	}

	// 防止缓存穿透的查询方法
//	public Shop queryWithThrough(Long id) {
//
//		// 从redis缓存中取
//		String key = CACHE_SHOP_KEY + id.toString();
//		String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//		// 存在（shopJson不为空且不为占位符），直接返回
//		if (!StringUtils.isNotBlank(shopJson) && !EMPTY_PLACEHOLDER.equals(shopJson)) {
//			return JSONUtil.toBean(shopJson, Shop.class);
//		}
//
//		// 缓存的值为占位符，则说明该数据在数据库中不存在，直接返回错误信息
//		if(EMPTY_PLACEHOLDER.equals(shopJson)){
//			return null;
//		}
//
//		// 如果缓存中不存在，到数据库里查
//		Shop shop = getById(id);
//
//		if (shop == null){
//			// 解决缓存穿透问题，缓存占位符
//			stringRedisTemplate.opsForValue().set(key,EMPTY_PLACEHOLDER,CACHE_NULL_TTL,TimeUnit.MINUTES);
//			return null;
//		}
//
//		// 将shop添加到缓存中
//		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//		return shop;
//	}

	@Override
	public Result updateShop(Shop shop) {
		// 先更新数据库
		updateById(shop);
		// 再删除缓存
		stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
		return Result.ok();
	}

	public void saveData2Redis(Long id, Long expireSeconds){
		// 获取店铺信息
		Shop shop = getById(id);

		// 设置逻辑过期时间
		RedisData redisData = new RedisData();
		redisData.setData(shop);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

		// 写入缓存
		stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
	}


	// 获取锁的方法
	private boolean tryLock(String key){
		Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
		return BooleanUtil.isTrue(flag);
	}

	// 释放锁
	private void unLock(String key){
		stringRedisTemplate.delete(key);
	}
}
