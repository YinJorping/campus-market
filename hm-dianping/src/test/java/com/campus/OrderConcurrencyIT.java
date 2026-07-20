package com.campus;

import com.campus.entity.Favorite;
import com.campus.entity.Item;
import com.campus.entity.Order;
import com.campus.enums.ItemStatus;
import com.campus.enums.OrderStatus;
import com.campus.mapper.FavoriteMapper;
import com.campus.mapper.ItemMapper;
import com.campus.mapper.OrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.hmdp.utils.RedisConstants.GEO_ITEM_KEY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CampusMarket Backend Acceptance — Concurrency Tests
 *
 * TC-S4: Two buyers concurrently order same item → only one wins (CAS)
 * TC-S5: Seller confirm vs Buyer cancel race → only one state change wins
 * TC-S6: Concurrent duplicate favorite → UNIQUE key protects idempotency
 * TC-S7: Concurrent duplicate unfavorite → idempotent, no errors
 */
@SpringBootTest(classes = com.hmdp.HmDianPingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderConcurrencyIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ItemMapper itemMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static volatile String tokenA;   // seller (user_0li9limv5e, id=1011)
    private static volatile String tokenB;   // buyer 1 (user_v12biugyse, id=1014)
    private static volatile String tokenC;   // buyer 2

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private Long cleanupItemId;
    private Long cleanupOrderId;

    // ── helpers ──────────────────────────────────────────────────

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("authorization", token);
        return h;
    }

    private HttpHeaders authJson(String token) {
        HttpHeaders h = authHeader(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> get(String path, String token) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET,
                new HttpEntity<>(null, authHeader(token)), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token, String json) {
        return restTemplate.postForEntity(baseUrl() + path,
                new HttpEntity<>(json, authJson(token)), Map.class);
    }

    private ResponseEntity<Map> put(String path, String token) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.PUT,
                new HttpEntity<>(null, authHeader(token)), Map.class);
    }

    private void del(String path, String token) {
        restTemplate.exchange(baseUrl() + path, HttpMethod.DELETE,
                new HttpEntity<>(null, authHeader(token)), Map.class);
    }

    private String login(String phone) throws InterruptedException {
        restTemplate.postForEntity(baseUrl() + "/user/code?phone=" + phone, null, Map.class);
        Thread.sleep(600);
        String code = stringRedisTemplate.opsForValue().get("login:code:" + phone);
        assertNotNull(code, "Verification code not found in Redis for " + phone);
        ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl() + "/user/login",
                new HttpEntity<>("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}", authJson("")), Map.class);
        Map body = resp.getBody();
        assertTrue((Boolean) body.get("success"), "Login failed for " + phone + ": " + body);
        return (String) body.get("data");
    }

    private synchronized void ensureLoggedIn() throws InterruptedException {
        if (tokenA != null) return;
        tokenA = login("13800138000");
        tokenB = login("13900139000");
        tokenC = login("13700137000");
        System.out.println("=== All 3 tokens acquired ===");
    }

    private Long createItem(String token, String name, double x, double y) {
        String json = String.format(
                "{\"name\":\"%s\",\"categoryId\":2,\"images\":\"x.jpg\",\"price\":1000," +
                "\"campus\":\"c\",\"meetPlace\":\"m\",\"itemCondition\":3,\"x\":%f,\"y\":%f}",
                name, x, y);
        ResponseEntity<Map> resp = post("/api/items", token, json);
        Map body = resp.getBody();
        assertTrue((Boolean) body.get("success"), "Create item failed: " + body);
        return ((Number) body.get("data")).longValue();
    }

    private Long createOrder(String token, Long itemId) {
        ResponseEntity<Map> resp = post("/api/orders", token, "{\"itemId\":" + itemId + "}");
        Map body = resp.getBody();
        assertTrue((Boolean) body.get("success"), "Create order failed: " + body);
        return ((Number) body.get("data")).longValue();
    }

    // ── cleanup ──────────────────────────────────────────────────

    @AfterEach
    void tearDown() {
        if (cleanupOrderId != null) {
            orderMapper.deleteById(cleanupOrderId);
            cleanupOrderId = null;
        }
        if (cleanupItemId != null) {
            itemMapper.deleteById(cleanupItemId);
            cleanupItemId = null;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TC-S4: Two buyers concurrently order same item → only one wins
    // ══════════════════════════════════════════════════════════════

    @Test
    void twoBuyersConcurrentlyOrderSameItem_onlyOneSucceeds() throws Exception {
        ensureLoggedIn();

        // 1. seller A creates item
        Long itemId = createItem(tokenA, "CONCURRENT-CAS", 116.4, 39.91);
        cleanupItemId = itemId;
        System.out.println("[TC-S4] Created item: " + itemId);

        // 2. launch 2 concurrent buyers
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Long> winnerOrderId = new AtomicReference<>();

        executor.submit(() -> {
            try {
                gate.await();
                ResponseEntity<Map> resp = post("/api/orders", tokenB, "{\"itemId\":" + itemId + "}");
                Map b = resp.getBody();
                if ((Boolean) b.get("success")) {
                    successCount.incrementAndGet();
                    winnerOrderId.set(((Number) b.get("data")).longValue());
                }
                System.out.println("[TC-S4] Buyer B -> " + b);
            } catch (Exception e) {
                System.err.println("[TC-S4] Buyer B error: " + e.getMessage());
            } finally {
                done.countDown();
            }
        });

        executor.submit(() -> {
            try {
                gate.await();
                ResponseEntity<Map> resp = post("/api/orders", tokenC, "{\"itemId\":" + itemId + "}");
                Map b = resp.getBody();
                if ((Boolean) b.get("success")) {
                    successCount.incrementAndGet();
                    winnerOrderId.set(((Number) b.get("data")).longValue());
                }
                System.out.println("[TC-S4] Buyer C -> " + b);
            } catch (Exception e) {
                System.err.println("[TC-S4] Buyer C error: " + e.getMessage());
            } finally {
                done.countDown();
            }
        });
        Thread.sleep(200);
        gate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Threads did not finish in time");

        // 3. assertions
        assertEquals(1, successCount.get(), "Exactly 1 order must succeed");
        cleanupOrderId = winnerOrderId.get();

        Item item = itemMapper.selectById(itemId);
        assertEquals(ItemStatus.RESERVED.getValue(), item.getStatus(), "Item must be RESERVED");

        int orderCount = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getItemId, itemId));
        assertEquals(1, orderCount, "Exactly 1 order record must exist");

        Double geoScore = stringRedisTemplate.opsForZSet().score(GEO_ITEM_KEY, itemId.toString());
        assertNull(geoScore, "Item must be removed from GEO");

        System.out.println("[TC-S4] PASS — CAS concurrency verified, winner order=" + winnerOrderId.get());
    }

    // ══════════════════════════════════════════════════════════════
    // TC-S5: Seller confirm vs Buyer cancel race → only one wins
    // ══════════════════════════════════════════════════════════════

    @Test
    void sellerConfirmVsBuyerCancel_raceCondition_onlyOneStateChangeWins() throws Exception {
        ensureLoggedIn();

        // 1. seller A creates item, buyer B creates order
        Long itemId = createItem(tokenA, "CONCURRENT-RACE", 116.4, 39.91);
        cleanupItemId = itemId;
        Long orderId = createOrder(tokenB, itemId);
        cleanupOrderId = orderId;
        System.out.println("[TC-S5] item=" + itemId + " order=" + orderId + " (PENDING)");

        // 2. concurrent: seller confirm vs buyer cancel
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger confirmOk = new AtomicInteger(0);
        AtomicInteger cancelOk = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                gate.await();
                ResponseEntity<Map> resp = put("/api/orders/" + orderId + "/confirm", tokenA);
                if ((Boolean) resp.getBody().get("success")) confirmOk.incrementAndGet();
                System.out.println("[TC-S5] Confirm -> " + resp.getBody());
            } catch (Exception e) {
                System.err.println("[TC-S5] Confirm error: " + e.getMessage());
            } finally {
                done.countDown();
            }
        });

        executor.submit(() -> {
            try {
                gate.await();
                ResponseEntity<Map> resp = put("/api/orders/" + orderId + "/cancel", tokenB);
                if ((Boolean) resp.getBody().get("success")) cancelOk.incrementAndGet();
                System.out.println("[TC-S5] Cancel -> " + resp.getBody());
            } catch (Exception e) {
                System.err.println("[TC-S5] Cancel error: " + e.getMessage());
            } finally {
                done.countDown();
            }
        });

        Thread.sleep(200);
        gate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Threads did not finish in time");

        // 3. assertions
        assertEquals(1, confirmOk.get() + cancelOk.get(),
                "Exactly one (confirm or cancel) must succeed");

        Order order = orderMapper.selectById(orderId);
        int orderStatus = order.getStatus();
        assertTrue(
                orderStatus == OrderStatus.CONFIRMED.getValue() ||
                orderStatus == OrderStatus.CANCELLED.getValue(),
                "Order must be CONFIRMED or CANCELLED, actual=" + orderStatus);

        Item item = itemMapper.selectById(itemId);
        if (orderStatus == OrderStatus.CANCELLED.getValue()) {
            assertEquals(ItemStatus.ON_SALE.getValue(), item.getStatus(), "If cancelled, item=ON_SALE");
        } else {
            assertEquals(ItemStatus.RESERVED.getValue(), item.getStatus(), "If confirmed, item=RESERVED");
        }

        System.out.println("[TC-S5] PASS — Final: order=" + orderStatus + " item=" + item.getStatus());
    }

    // ══════════════════════════════════════════════════════════════
    // TC-S6: Concurrent duplicate favorite → UNIQUE key protects
    // ══════════════════════════════════════════════════════════════

    @Test
    void concurrentDuplicateFavorite_onlyOneRecordCreated() throws Exception {
        ensureLoggedIn();

        Long itemId = createItem(tokenA, "CONCURRENT-FAV", 0, 0);
        cleanupItemId = itemId;
        // ensure clean state
        del("/api/items/" + itemId + "/favorite", tokenB);

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Runnable favTask = () -> {
            try {
                gate.await();
                ResponseEntity<Map> resp = post("/api/items/" + itemId + "/favorite", tokenB, null);
                System.out.println("[TC-S6] Fav -> " + (resp.getBody() != null ? resp.getBody() : "OK"));
            } catch (Exception e) {
                System.err.println("[TC-S6] Fav error: " + e.getMessage());
            } finally {
                done.countDown();
            }
        };

        executor.submit(favTask);
        executor.submit(favTask);
        Thread.sleep(200);
        gate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Threads did not finish in time");

        // verify exactly 1 record
        int count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getItemId, itemId));
        assertEquals(1, count, "Duplicate favorite must be prevented by UNIQUE constraint");

        // cleanup
        del("/api/items/" + itemId + "/favorite", tokenB);
        System.out.println("[TC-S6] PASS — Favorite idempotency verified");
    }

    // ══════════════════════════════════════════════════════════════
    // TC-S7: Concurrent duplicate unfavorite → idempotent, no errors
    // ══════════════════════════════════════════════════════════════

    @Test
    void concurrentDuplicateUnfavorite_idempotentNoErrors() throws Exception {
        ensureLoggedIn();

        Long itemId = createItem(tokenA, "CONCURRENT-UNFAV", 0, 0);
        cleanupItemId = itemId;
        // pre-favorite
        post("/api/items/" + itemId + "/favorite", tokenB, null);

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Runnable unfavTask = () -> {
            try {
                gate.await();
                del("/api/items/" + itemId + "/favorite", tokenB);
                System.out.println("[TC-S7] Unfav done by " + Thread.currentThread().getName());
            } catch (Exception e) {
                System.err.println("[TC-S7] Unfav error: " + e.getMessage());
            } finally {
                done.countDown();
            }
        };

        executor.submit(unfavTask);
        executor.submit(unfavTask);
        Thread.sleep(200);
        gate.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Threads did not finish in time");

        // verify 0 records
        int count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<Favorite>().eq(Favorite::getItemId, itemId));
        assertEquals(0, count, "Favorite record must be deleted");

        // verify idempotent: unfavorite again should not throw
        del("/api/items/" + itemId + "/favorite", tokenB);
        System.out.println("[TC-S7] PASS — Unfavorite idempotency verified");
    }
}
