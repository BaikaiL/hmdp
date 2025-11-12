package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import jdk.vm.ci.meta.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

	private String lockName;
	private StringRedisTemplate stringRedisTemplate;
	private final static String KEY_PREFIX = "lock:";
	private final static String ID_PREFIEX = UUID.randomUUID().toString(true) + "-";
	private final static DefaultRedisScript<Long> UNLOCK_SCRIPT;

	/* static构造
	单例复用：脚本对象在类加载时初始化，全局只存在一个实例。
	性能更好：避免重复解析与对象创建，减少 GC 压力。
	线程安全：静态常量在类加载完成后是只读的，可安全共享。
	更适合热路径（高频调用）：如库存扣减、分布式锁、计数器等操作

	初始化时机固定：在类加载时创建，不适合动态脚本。
	脚本不可动态更改：一旦初始化，内容固定。

	适用场景
	固定 Lua 脚本、高频使用、全局共享。
	Spring Boot 项目中推荐放在 @Configuration 或工具类的静态变量中
	 */
	static {
		UNLOCK_SCRIPT = new DefaultRedisScript<>();
		UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
		UNLOCK_SCRIPT.setResultType(Long.class);
	}

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
		// 调用lua脚本
		stringRedisTemplate.execute(UNLOCK_SCRIPT,
				Collections.singletonList(KEY_PREFIX + lockName),
				ID_PREFIEX + Thread.currentThread().getId());
	}

//	@Override
//	public void unLock() {
//		// 获取锁的value
//		String threadId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + lockName);
//		// 比较锁的value是否等于本线程的id
//		String id = ID_PREFIEX + Thread.currentThread().getId();
//		// 只有二者相等时才删除锁
//		if(id.equals(threadId)){
//			stringRedisTemplate.delete(KEY_PREFIX + lockName);
//		}
//	}
}
