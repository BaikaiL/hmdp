package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Component
public class LoginInterceptor implements HandlerInterceptor {

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
		// 从session中获取token
		String token = request.getHeader("Authorization");

		if(token == null || token.isEmpty()){
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}

		// 从redis中获取用户
		String key = LOGIN_USER_KEY+token;
		Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
		// 判断用户是否存在
		if(userMap.isEmpty()){
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
		// 将map转为dto对象
		UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

		// 将查询到的用户添加到threadlocal中
		UserHolder.saveUser(userDTO);

		// 刷新token有效期
		stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.SECONDS);

		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		UserHolder.removeUser();
	}
}
