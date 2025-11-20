package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.text.BreakIterator;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

	@Resource
	private IVoucherService voucherService;

	@Resource
	private RedisWorker redisWorker;

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Resource
	private RedissonClient redissonClient;

	private final static DefaultRedisScript<Long> SECKILL_SCRIPT;
	private static final String STREAM_KEY = "stream.orders";
	private IVoucherOrderService proxy;

	//阻塞队列
//	private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

	// 线程池
	private final static ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

	private class SeckillOrderHandle implements Runnable{
		String queueName = STREAM_KEY;
		@Override
		public void run() {
			while (true){
				try {
					// 从消息队列中取出订单信息， XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.order >
					List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
							Consumer.from("g1", "c1"),
							StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2L)),
							StreamOffset.create(queueName, ReadOffset.lastConsumed())
					);

					// 检查list
					if(list == null || list.isEmpty()){
						// 继续下一次循环
						continue;
					}

					// list非空，解析list后进行下单操作
					MapRecord<String, Object, Object> record = list.get(0);
					Map<Object, Object> value = record.getValue();
					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
					handleVoucherOder(voucherOrder);

					// 进行ack却确认 SACK stream.order g1 id
					stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
				} catch (Exception e) {
					log.error("处理订单异常", e);
					handlePendingList();
				}
			}
		}

		private void handlePendingList() {
			while(true){
				try{
					// 从pending-list队列中取出订单信息， XREADGROUP GROUP g1 c1 COUNT 1  STREAMS stream.order 0
					List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
							Consumer.from("g1", "c1"),
							StreamReadOptions.empty().count(1),
							StreamOffset.create(queueName, ReadOffset.from("0"))
					);

					// 检查list
					if(list == null || list.isEmpty()){
						// 结束循环
						break;
					}

					// list非空，解析list后进行下单操作
					MapRecord<String, Object, Object> record = list.get(0);
					Map<Object, Object> value = record.getValue();
					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
					handleVoucherOder(voucherOrder);

					// 进行ack却确认 SACK stream.order g1 id
					stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
				} catch (Exception e) {
					log.error("处理pending-list异常，已跳过该消息");
					break;
				}
			}
		}

		private void handleVoucherOder(VoucherOrder voucherOrder) {
			Long userId = voucherOrder.getUserId();
			RLock lock = redissonClient.getLock("lock:order" + userId);
			boolean isLock = lock.tryLock(); // 默认重试-1，不重试，超时时间30s
			// 获取锁失败
			if(!isLock){
				log.error("获取锁失败");
				return;
			}

			try {
				proxy.createVoucherOrder(voucherOrder);
			}
			finally {
				// 释放锁
				lock.unlock();
			}
		}
	}

//	private class SeckillOrderHandle implements Runnable{
//
//		@Override
//		public void run() {
//			while (true){
//				try {
//					VoucherOrder voucherOrder = orderTasks.take();
//					handleVoucherOder(voucherOrder);
//				} catch (Exception e) {
//					log.error("处理订单异常", e);
//				}
//			}
//		}
//	}

	@PostConstruct
	private void init(){
		initStream();
		SECKILL_ORDER_EXECUTOR.submit(new SeckillOrderHandle());
	}

	private void initStream() {
		String queueName = STREAM_KEY;
		Boolean exists = stringRedisTemplate.hasKey(queueName);
		if (Boolean.FALSE.equals(exists)) {
			stringRedisTemplate.opsForStream().add(
					StreamRecords.mapBacked(Collections.singletonMap("init", "init"))
							.withStreamKey(queueName)
			);
		}
		try {
			stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.from("0"), "g1");
		} catch (RedisSystemException e) {
			String message = e.getMessage();
			if (message == null || !message.contains("BUSYGROUP")) {
				throw e;
			}
		}
	}

	static {
		SECKILL_SCRIPT = new DefaultRedisScript<>();
		SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
		SECKILL_SCRIPT.setResultType(Long.class);
	}

	@Override
	public Result seckill(Long voucherId) {

		Long userId = UserHolder.getUser().getId();
		long orderId = redisWorker.nextId("order");
		// 执行lua脚本
		Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
				Collections.emptyList(),
				voucherId.toString(), userId.toString(), String.valueOf(orderId));
		int r = result.intValue();
		// 判断结果是否为0
		if(r != 0){
			return Result.fail(r == 1 ? "库存不足" : "不可以重复下单");
		}

		// 获取代理对象，防止事务失效
		this.proxy = (IVoucherOrderService) AopContext.currentProxy();

		// 返回订单id
		return Result.ok(orderId);

	}


//	@Override
//	public Result seckill(Long voucherId) {
//
//		Long userId = UserHolder.getUser().getId();
//		// 执行lua脚本
//		Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//				Collections.emptyList(),
//				voucherId.toString(), userId.toString());
//		int r = result.intValue();
//		// 判断结果是否为0
//		if(r != 0){
//			return Result.fail(r == 1 ? "库存不足" : "不可以重复下单");
//		}
//
//		// 为0，可以下单，把下单信息保存到阻塞队列
//		// 创建订单
//		VoucherOrder voucherOrder = new VoucherOrder();
//		// 设置全局唯一id
//		long orderId = redisWorker.nextId("order");
//		voucherOrder.setVoucherId(orderId);
//		voucherOrder.setUserId(userId);
//
//		// 获取代理对象，防止事务失效
//		this.proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//		// 保存到阻塞队列
//		orderTasks.add(voucherOrder);
//
//		// 返回订单id
//		return Result.ok(orderId);
//
//	}




//	@Override
//	public Result seckill(Long voucherId) {
//
//		// 获取优惠券
//		Voucher voucher = voucherService.getById(voucherId);
//		// 判断是否在时间内
//		if(voucher.getBeginTime().isAfter(LocalDateTime.now()) || voucher.getEndTime().isBefore(LocalDateTime.now())){
//			return Result.fail("时间非法");
//		}
//		// 判断库存
//		if(voucher.getStock() < 1){
//			return Result.fail("库存不足");
//		}
//
//		// 对用户id进行加锁操作
//		Long userId = UserHolder.getUser().getId();
//
//		// 尝试获取锁
/// /		SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
//		RLock lock = redissonClient.getLock("lock:order" + userId);
//		boolean isLock = lock.tryLock(); // 默认重试-1，不重试，超时时间30s
//		// 获取锁失败
//		if(!isLock){
//			return Result.fail("不允许重复下单");
//		}
//
//		try {
//			// 获取代理对象，防止事务失效
//			IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//			return proxy.createVoucherOrder(voucherId);
//		}
//		finally {
//			// 释放锁
//			lock.unlock();
//		}
//
//	}

	@Transactional
	public void createVoucherOrder(VoucherOrder voucherOrder) {
		// 扣减库存，乐观锁解决超卖
		boolean update = voucherService.update().setSql("stock = stock - 1")
				.eq("voucher_id", voucherOrder.getVoucherId())
				.gt("stock", 0)
				.update();

		// 更新失败，返回库存不足
		if(!update){
			log.error("库存不足");
		}
		save(voucherOrder);
	}
}
