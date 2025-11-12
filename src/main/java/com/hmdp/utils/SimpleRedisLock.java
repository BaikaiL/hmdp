package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import jdk.vm.ci.meta.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

	private String lockName;
	private StringRedisTemplate stringRedisTemplate;
	private final static String KEY_PREFIX = "lock:";
	private final static String ID_PREFIEX = UUID.randomUUID().toString(true);

	public SimpleRedisLock(String lockName, StringRedisTemplate stringRedisTemplate) {
		this.lockName = lockName;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	@Override
	public boolean tryLock(Long timeOutSec) {
		// key为前缀 + 用户传入名字， value为线程标识，同一jvm的线程id一般不同
		String threadId = ID_PREFIEX + Thread.currentThread().getId();
		stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockName, threadId, timeOutSec, TimeUnit.SECONDS);
		return false;
	}

	@Override
	public void unLock() {
		// 获取锁的value
		String threadId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + lockName);
		// 比较锁的value是否等于本线程的id
		String id = ID_PREFIEX + Thread.currentThread().getId();
		// 只有二者相等时才删除锁
		if(id.equals(threadId)){
			stringRedisTemplate.delete(KEY_PREFIX + lockName);
		}
	}
}
