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
import org.springframework.stereotype.Service;

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

		// 创建订单
		VoucherOrder voucherOrder = new VoucherOrder();

		// 设置全局唯一id
		long id = redisWorker.nextId("order");
		voucherOrder.setVoucherId(id);

		long userId = UserHolder.getUser().getId();
		voucherOrder.setUserId(userId);

		save(voucherOrder);

		// 返回id
		return Result.ok(id);
	}
}
