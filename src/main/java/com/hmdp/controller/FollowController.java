package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

	private IFollowService followService;

	@PutMapping("/{id}/{isFollow}")
	public Result follow(@PathVariable("id") Long followedUserId, @PathVariable("isFollow") Boolean isFollow){
		return followService.follow(followedUserId, isFollow);
	}

	@GetMapping("/or/not/{id}")
	public Result isFollow(@PathVariable Long id){
		return followService.isFollow(id);
	}

	@GetMapping("/common/{id}")
	public Result commonFollows(@PathVariable Long id){
		return followService.commonFollows(id);
	}

}
