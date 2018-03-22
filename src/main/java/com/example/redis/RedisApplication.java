package com.example.redis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Reference;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Things to demo:
 * - redisTemplate and serialization
 * - keys and values
 * - repositories
 * - caching
 * - pub/sub
 * - sessions
 */
@Log
@EnableRedisHttpSession
@EnableCaching
@SpringBootApplication
public class RedisApplication {

	private final RedisConnectionFactory connectionFactory;

	public RedisApplication(RedisConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	private Long generateNextId() {
		long tmpLong = new Random().nextLong();
		return Math.max(tmpLong, tmpLong * -1);
	}

	private ApplicationRunner title(String title, ApplicationRunner runner) {
		Assert.hasText(title, "there should be a title");
		return args -> {
			log.info(title.toUpperCase() + ":");
			runner.run(args);
		};
	}

	@Bean
	CacheManager cacheManager() {
		return RedisCacheManager
				.builder(this.connectionFactory)
				.build();
	}

	@Bean
	RedisMessageListenerContainer redisListener() {
		MessageListener messageListener = (msg, pattern) -> {
			String str = new String(msg.getBody());
			log.info("message from 'chat': " + str);
		};
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(this.connectionFactory);
		container.addMessageListener(messageListener, new PatternTopic("chat"));
		return container;
	}

	@Bean
	ApplicationRunner geography(RedisTemplate<String, String> rt) {
		return title("Geography", args -> {
			GeoOperations<String, String> opsForGeo = rt.opsForGeo();

			opsForGeo.add("Sicily", new Point(13.361389, 38.115556), "Arigento");
			opsForGeo.add("Sicily", new Point(15.087269, 37.502669), "Catania");
			opsForGeo.add("Sicily", new Point(13.583333, 37.316667), "Palermo");

			Circle circle = new Circle(new Point(13.583333, 37.316667), //
					new Distance(100, RedisGeoCommands.DistanceUnit.KILOMETERS));
			GeoResults<RedisGeoCommands.GeoLocation<String>> result = opsForGeo.radius("Sicily", circle);
			log.info(result.toString());
			result
					.getContent()
					.forEach(gr -> log.info(gr.toString()));
		});
	}

	@Bean
	ApplicationRunner repositories(OrderRepository orderRepository, LineItemRepository lineItemRepository) {
		return title("Hash Repositories", args -> {

			Long orderId = generateNextId();

			List<LineItem> items = Arrays.asList(
					new LineItem(orderId, generateNextId(), "plunger"),
					new LineItem(orderId, generateNextId(), "soup"),
					new LineItem(orderId, generateNextId(), "coffee mug"));

			items
					.stream()
					.map(lineItemRepository::save)
					.forEach(li -> log.info("saved " + li.toString()));

			lineItemRepository.findByOrderId(items.iterator().next().getOrderId())
					.forEach(li -> log.info("found " + LineItem.class.getName() + ':' + li.getOrderId() + ':' + li.getId()));

			Order order = new Order(generateNextId(), new Date(), items);
			orderRepository.save(order);

			Optional<Order> byId = orderRepository.findById(order.getId());
			byId.ifPresent(o -> log.info("saved " + o.toString()));
		});
	}

	@Bean
	ApplicationRunner publishSubscribe(RedisTemplate<String, String> rt) {
		return title("Publish/Subscribe", args -> {
			log.info("sending message to 'chat'");
			rt.convertAndSend("chat", "Hello, world");
		});
	}

	private long measure(Runnable runnable) {
		long start = System.currentTimeMillis();
		runnable.run();
		return System.currentTimeMillis() - start;
	}

	@Bean
	ApplicationRunner cache(OrderService orderService) {
		return title("Caching", args -> {
			log.info("first: " + measure(() -> orderService.byId(1L)));
			log.info("second: " + measure(() -> orderService.byId(1L)));
			log.info("third: " + measure(() -> orderService.byId(1L)));
		});
	}

	public static void main(String[] args) {
		SpringApplication.run(RedisApplication.class, args);
	}
}

@Log
@Controller
@SessionAttributes("cart")
class SessionServlet {

	private final AtomicLong id = new AtomicLong();

	@ModelAttribute("cart")
	ShoppingCart cart() {
		log.info("cart being created");
		return new ShoppingCart();
	}

	@GetMapping("/orders")
	String orders(@ModelAttribute("cart") ShoppingCart cart, Model model) {
		cart.addOrder(new Order(id.incrementAndGet(), new Date(), Collections.emptyList()));
		model.addAttribute("orders", cart.getOrders());
		return "orders";
	}
}

class ShoppingCart implements Serializable {

	private final Collection<Order> orders = new ArrayList<>();

	public void addOrder(Order order) {
		this.orders.add(order);
	}

	public Collection<Order> getOrders() {
		return orders;
	}
}

@Service
class OrderService {

	private final Map<Long, Order> orders = new ConcurrentHashMap<>();

	OrderService() {
		Long id = 0L;
		List<Order> orderList = Arrays.asList(
				new Order(++id, new Date(), Collections.emptyList()),
				new Order(++id, new Date(), Collections.emptyList()),
				new Order(++id, new Date(), Collections.emptyList()));
		orderList.forEach(o -> this.orders.put(o.getId(), o));
	}

	@Cacheable("order")
	public Order byId(Long id) {
		try {
			Thread.sleep(1000 * 10);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		return this.orders.get(id);
	}
}

interface LineItemRepository extends CrudRepository<LineItem, Long> {

	Collection<LineItem> findByOrderId(Long id);
}

interface OrderRepository extends CrudRepository<Order, Long> {

	Collection<Order> findByDate(Date date);
}

@RedisHash
@Data
@AllArgsConstructor
@NoArgsConstructor
class Order implements Serializable {

	@Id
	private Long id;

	@Indexed
	private Date date;

	@Reference
	private List<LineItem> lineItems;

}

@RedisHash
@Data
@AllArgsConstructor
@NoArgsConstructor
class LineItem implements Serializable {

	// look up this object by its orderId
	@Indexed
	private Long orderId;

	@Id
	private Long id;

	private String description;
}
