package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/*
	缓存工具类
* **/
@Slf4j
@Component
public class CacheClient {
	private final StringRedisTemplate stringRedisTemplate;

	private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(10);

	public CacheClient(StringRedisTemplate stringRedisTemplate){
		this.stringRedisTemplate = stringRedisTemplate;
	}

	public void set(String key, Object value, Long time, TimeUnit unit){
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
	}

	/**
	 * 逻辑过期的方法新建key，value
	 * @param key
	 * @param value
	 * @param time
	 * @param unit
	 */
	public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
		// 设置逻辑过期
		RedisData redisData = new RedisData();
		redisData.setData(value);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
	}

	// 防止缓存穿透的查询方法
	public <R, ID> R queryWithThrough(
			String prefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit
	) {

		// 从redis缓存中取
		String key = prefix + id.toString();
		String json = stringRedisTemplate.opsForValue().get(key);

		// 存在（shopJson不为空且不为占位符），直接返回
		if (!StringUtils.isNotBlank(json) && !EMPTY_PLACEHOLDER.equals(json)) {
			return JSONUtil.toBean(json, type);
		}

		// 缓存的值为占位符，则说明该数据在数据库中不存在，直接返回错误信息
		if(EMPTY_PLACEHOLDER.equals(json)){
			return null;
		}

		// 如果缓存中不存在，到数据库里查
		R r = dbFallBack.apply(id);

		if (r == null){
			// 解决缓存穿透问题，缓存占位符
			stringRedisTemplate.opsForValue().set(key,EMPTY_PLACEHOLDER,time,unit);
			return null;
		}

		set(key, r, time, unit);

		return r;
	}


	// 防止缓存击穿的查询方法，逻辑过期实现
	public <R, ID> R queryWithLogicalExpire(
			String prefix, ID id, Class<R> type, String lockPrefix, Function<ID, R> dbFallBack, Long time, TimeUnit unit
	) {

		// 从redis缓存中取
		String key = prefix + id.toString();
		String json = stringRedisTemplate.opsForValue().get(key);

		// 不存在存在（shopJson为空），直接返回
		if (StringUtils.isNotBlank(json)) {
			return null;
		}

		// 存在，判断过期时间
		//反序列化
		RedisData redisData = JSONUtil.toBean(json, RedisData.class);
		R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
		LocalDateTime expire = redisData.getExpireTime();

		if(expire.isAfter(LocalDateTime.now())) {
			// 未过期，直接返回
			return r;
		}
		// 过期，缓存重建
		// 获取锁
		String lockKey = lockPrefix + id;
		boolean isLock = tryLock(lockKey);

		// 获取锁成功
		if(isLock){
			try {
				EXECUTOR_SERVICE.submit(()->{
					R r1 = dbFallBack.apply(id);
					this.setWithLogicalExpire(key, r1, time, unit);
				});
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				unLock(lockKey);
			}
		}

		// 获取锁失败，返回旧数据
		return r;
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
