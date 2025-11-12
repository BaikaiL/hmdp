package com.hmdp.utils;

import jdk.vm.ci.meta.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

	private String lockName;
	private StringRedisTemplate stringRedisTemplate;
	private final static String KEY_PREFIX = "lock:";

	public SimpleRedisLock(String lockName, StringRedisTemplate stringRedisTemplate) {
		this.lockName = lockName;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	@Override
	public boolean tryLock(Long timeOutSec) {
		// key为前缀 + 用户传入名字， value为线程id，同一jvm的线程id一般不同
		long threadId = Thread.currentThread().getId();
		stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockName, threadId + "", timeOutSec, TimeUnit.SECONDS);
		return false;
	}

	@Override
	public void unLock() {
		stringRedisTemplate.delete(KEY_PREFIX + lockName);
	}
}
