package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

	@Resource
	private IVoucherService voucherService;

	@Resource
	private RedisWorker redisWorker;

	@Override
	public Result seckill(Long voucherId) {

		// 获取优惠券
		Voucher voucher = voucherService.getById(voucherId);
		// 判断是否在时间内
		if(voucher.getBeginTime().isAfter(LocalDateTime.now()) || voucher.getEndTime().isBefore(LocalDateTime.now())){
			return Result.fail("时间非法");
		}
		// 判断库存
		if(voucher.getStock() < 1){
			return Result.fail("库存不足");
		}

		// 对用户id进行加锁操作
		Long userId = UserHolder.getUser().getId();
		synchronized (userId.toString().intern()){
			// 获取代理对象，防止事务失效
			IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
			return proxy.createVoucherOrder(voucherId);
		}
	}

	@Transactional
	public Result createVoucherOrder(Long voucherId) {
		// 扣减库存，乐观锁解决超卖
		boolean update = voucherService.update().setSql("stock = stock - 1")
				.eq("voucher_id", voucherId)
				.gt("stock", 0)
				.update();

		// 更新失败，返回库存不足
		if(!update){
			return Result.fail("库存不足");
		}

		// 创建订单
		VoucherOrder voucherOrder = new VoucherOrder();

		// 设置全局唯一id
		Long id = redisWorker.nextId("order");
		voucherOrder.setVoucherId(id);

		Long userId = UserHolder.getUser().getId();
		voucherOrder.setUserId(userId);

		save(voucherOrder);

		// 返回id
		return Result.ok(id);
	}
}
