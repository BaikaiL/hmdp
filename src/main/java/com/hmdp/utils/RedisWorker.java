package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/*
	自增长id
 */
@Component
public class RedisWorker {

	private final StringRedisTemplate stringRedisTemplate;
	// 开始时间戳
	private static final long BEGIN_TIMESTAMP = 1735689600L;
	// 位运算位数
	private static final int COUNT_BITS = 32;

	public RedisWorker(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}

	public long nextId(String keyPrefix){

		// id 共64位， 第1位为符号位，第2-32是时间戳，第33-64为序列号
		// 1.生成时间戳
		LocalDateTime now = LocalDateTime.now();
		long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
		long timestamp = nowSecond - BEGIN_TIMESTAMP;

		// 2.获取自增长的序列号
		// 获取当前时间
		String day = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		// 自增长设置
		long curr = stringRedisTemplate.opsForValue().increment("inc" + keyPrefix + ":" + day);

		return timestamp << COUNT_BITS | curr;
	}

	public static void main(String[] args) {
		LocalDateTime begin = LocalDateTime.of(2025,1,1,0,0,0);
		System.out.println(begin.toEpochSecond(ZoneOffset.UTC));
	}
}
