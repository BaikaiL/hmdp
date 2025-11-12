package com.hmdp.utils;

public interface ILock {
	/**
	 * 尝试获取锁
	 * @param timeOutSec
	 * @return
	 */
	boolean tryLock(Long timeOutSec);

	/**
	 * 释放锁
	 */
	void unLock();
}
