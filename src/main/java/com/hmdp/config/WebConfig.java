package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Autowired
	private LoginInterceptor loginInterceptor;

	@Autowired
	private RefreshTokenInterceptor refreshTokenInterceptor;

	public void addInterceptors(InterceptorRegistry registry){

		registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);

		registry.addInterceptor(loginInterceptor).excludePathPatterns(
				"/shop/**",
				"/voucher/**",
				"/shop-type/**",
				"/upload/**",
				"/blog/hot",
				"/user/code",
				"/user/login"
		).order(1);
	}
}
