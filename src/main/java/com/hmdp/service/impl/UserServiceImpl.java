package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import static com.hmdp.utils.ErrorConstants.INVALID_PHONE_NUMBER;
import static com.hmdp.utils.ErrorConstants.INVALID_VERIFICATION_CODE;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public Result sendCode(String phone) {

		// 检查手机号合法性
		if(RegexUtils.isPhoneInvalid(phone)){
			// 非法则返回错误信息
			return Result.fail(INVALID_PHONE_NUMBER);
		}

		// 生成验证码
		String code = RandomUtil.randomNumbers(6);

		// 保存到redis中
		stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

		log.debug("验证码是：" + code);
		return Result.ok();
	}

	@Override
	public Result login(LoginFormDTO loginForm, HttpSession session) {
		String phone = loginForm.getPhone();

		// 检查手机号合法性
		if(RegexUtils.isPhoneInvalid(phone)){
			// 非法则返回错误信息
			return Result.fail(INVALID_PHONE_NUMBER);
		}

		// 从redis中获取验证码
		String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
		String code = loginForm.getCode();
		// 比较验证码，若一致则在user表中查找用户
		if(cacheCode == null || !cacheCode.equals(code)){
			return Result.fail(INVALID_VERIFICATION_CODE);
		}

		// 查询用户是否存在
		User user = query().eq("phone", phone).one();

		if(user == null){
			user = createUserWithPhone(phone);
		}

		// 将用户信息存储在redis中
		String token = UUID.randomUUID().toString(true);
		UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

		// 将dto对象转为map
		Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
				CopyOptions.create().ignoreNullValue().setFieldValueEditor(
						(fieldName, fieldValue) -> fieldValue.toString()
				));


		stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token, userMap);
		stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.SECONDS);

		// 返回token
		return Result.ok(token);
	}

	private User createUserWithPhone(String phone) {
		User user = new User();
		user.setPhone(phone);
		//生成随机昵称
		user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
		baseMapper.insert(user);
		return user;
	}
}
