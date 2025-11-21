package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

	@Resource
	private StringRedisTemplate stringRedisTemplate;

	@Resource
	private IShopService shopService;

	@Test
	public void loadShop(){
		// 1.获取所有店铺信息
		List<Shop> shopList = shopService.list();
		// 2.把店铺分组，一个typeId一个组
		Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
		// 写入redis中
		for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
			// 获取同类型店铺id
			Long entryKey = entry.getKey();
			String geoKey = SHOP_GEO_KEY + entryKey;
			// 获取同类型店铺集合
			List<Shop> list = entry.getValue();
			List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(list.size());
			for(Shop shop : list){
				locations.add(new RedisGeoCommands.GeoLocation<>(
						shop.getId().toString(),
						new Point(shop.getX(), shop.getY())
				));
			}

			// 批量写入redis
			stringRedisTemplate.opsForGeo().add(geoKey, locations);
		}
	}

	@Test
	public void testHyperLogLog(){
		String[] value = new String[1000];
		int j = 0;
		for(int i = 0; i < 1000000; i++){
			j = i % 1000;
			value[j] = "user_" + i;
			if(j == 999){
				stringRedisTemplate.opsForHyperLogLog().add("hll1", value);
			}
		}
		System.out.println(stringRedisTemplate.opsForHyperLogLog().size("hll1"));
	}

}
