package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.OrderItemRequest;
import com.yasir.miniecommerce.dto.OrderRequest;
import com.yasir.miniecommerce.dto.OrderResponse;
import com.yasir.miniecommerce.exception.IdempotencyKeyConflictException;
import com.yasir.miniecommerce.exception.InsufficientStockException;
import com.yasir.miniecommerce.model.Product;
import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.ProductRepository;
import com.yasir.miniecommerce.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("mysql-test")
class OrderServiceMySqlIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private User secondUser;
    private Product firstProduct;
    private Product secondProduct;

    @BeforeEach
    void setUp() {
        String email = "order-integration-" + UUID.randomUUID() + "@example.invalid";
        user = userRepository.saveAndFlush(new User(email, "test-only", "USER"));
        firstProduct = productRepository.saveAndFlush(
                new Product("First " + UUID.randomUUID(), new BigDecimal("10.00"), 5)
        );
        secondProduct = productRepository.saveAndFlush(
                new Product("Second " + UUID.randomUUID(), new BigDecimal("3.50"), 1)
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        "",
                        List.of()
                )
        );
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();

        if (user != null && user.getId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM order_items WHERE order_id IN " +
                            "(SELECT id FROM orders WHERE user_id = ?)",
                    user.getId()
            );
            jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", user.getId());
        }
        if (secondUser != null && secondUser.getId() != null) {
            jdbcTemplate.update(
                    "DELETE FROM order_items WHERE order_id IN " +
                            "(SELECT id FROM orders WHERE user_id = ?)",
                    secondUser.getId()
            );
            jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", secondUser.getId());
        }
        if (firstProduct != null && firstProduct.getId() != null) {
            productRepository.deleteById(firstProduct.getId());
        }
        if (secondProduct != null && secondProduct.getId() != null) {
            productRepository.deleteById(secondProduct.getId());
        }
        if (user != null && user.getId() != null) {
            userRepository.deleteById(user.getId());
        }
        if (secondUser != null && secondUser.getId() != null) {
            userRepository.deleteById(secondUser.getId());
        }
    }

    @Test
    void createOrder_shouldPersistOwnerItemsPricesTotalAndReducedStock() {
        OrderRequest request = request(
                item(firstProduct.getId(), 2),
                item(secondProduct.getId(), 1)
        );

        OrderResponse response = orderService.createOrder(request, "success-key");

        assertEquals(user.getEmail(), response.getUserEmail());
        assertEquals(new BigDecimal("23.50"), response.getTotalPrice());
        assertEquals(2, response.getItems().size());
        assertEquals(3, productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
        assertEquals(0, productRepository.findById(secondProduct.getId()).orElseThrow().getStock());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                        Integer.class,
                        user.getId()
                )
        );
        assertEquals(
                user.getId(),
                jdbcTemplate.queryForObject(
                        "SELECT user_id FROM orders WHERE id = ?",
                        Long.class,
                        response.getId()
                )
        );
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_items WHERE order_id = ?",
                        Integer.class,
                        response.getId()
                )
        );
        assertEquals(
                new BigDecimal("10.00"),
                jdbcTemplate.queryForObject(
                        "SELECT price FROM order_items WHERE order_id = ? AND product_id = ?",
                        BigDecimal.class,
                        response.getId(),
                        firstProduct.getId()
                )
        );
    }

    @Test
    void createOrder_shouldRollbackAllStockAndOrderChanges_whenLaterItemHasInsufficientStock() {
        int firstStockBefore = firstProduct.getStock();
        int secondStockBefore = secondProduct.getStock();

        assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrder(request(
                        item(firstProduct.getId(), 2),
                        item(secondProduct.getId(), 2)
                ), "retry-after-stock-key")
        );

        assertEquals(firstStockBefore,
                productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
        assertEquals(secondStockBefore,
                productRepository.findById(secondProduct.getId()).orElseThrow().getStock());
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                        Integer.class,
                        user.getId()
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_items oi " +
                                "JOIN orders o ON o.id = oi.order_id WHERE o.user_id = ?",
                        Integer.class,
                        user.getId()
                )
        );
    }

    @Test
    void createOrder_shouldReplaySameOrderWithoutReducingStockAgain_whenKeyAndContentMatch() {
        OrderRequest request = request(item(firstProduct.getId(), 1));

        OrderResponse first = orderService.createOrder(request, "replay-key");
        OrderResponse replay = orderService.createOrder(request, "REPLAY-KEY");

        assertEquals(first.getId(), replay.getId());
        assertEquals(4, productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                Integer.class,
                user.getId()
        ));
    }

    @Test
    void createOrder_shouldRejectDifferentContentForSameKey_withoutChangingStock() {
        orderService.createOrder(request(item(firstProduct.getId(), 1)), "conflict-key");

        assertThrows(
                IdempotencyKeyConflictException.class,
                () -> orderService.createOrder(request(item(firstProduct.getId(), 2)), "conflict-key")
        );

        assertEquals(4, productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                Integer.class,
                user.getId()
        ));
    }

    @Test
    void createOrder_shouldScopeSameKeyToDifferentUsers() {
        secondUser = userRepository.saveAndFlush(
                new User("order-integration-second-" + UUID.randomUUID() + "@example.invalid", "test-only", "USER")
        );
        OrderRequest request = request(item(firstProduct.getId(), 1));

        OrderResponse firstUsersOrder = orderService.createOrder(request, "shared-key");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(secondUser.getEmail(), "", List.of())
        );
        OrderResponse secondUsersOrder = orderService.createOrder(request, "shared-key");

        assertEquals(false, firstUsersOrder.getId().equals(secondUsersOrder.getId()));
        assertEquals(3, productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }
    @Test
    void getMyOrders_shouldReturnOrdersWithItems() {
        // Arrange: Kullanıcı için bir sipariş oluştur.
        OrderRequest request = request(
                item(firstProduct.getId(), 2),
                item(secondProduct.getId(), 1)
        );

        OrderResponse createdOrder = orderService.createOrder(
                request,
                "get-my-orders-key"
        );

        // Act: Giriş yapan kullanıcının siparişlerini getir.
        List<OrderResponse> orders = orderService.getMyOrders();

        // Assert: Sipariş ve sipariş kalemleri doğru dönmeli.
        assertEquals(1, orders.size());

        OrderResponse response = orders.get(0);

        assertEquals(createdOrder.getId(), response.getId());
        assertEquals(user.getEmail(), response.getUserEmail());
        assertEquals(2, response.getItems().size());
        assertEquals(new BigDecimal("23.50"), response.getTotalPrice());

        assertNotNull(response.getItems().get(0).getProductId());
        assertNotNull(response.getItems().get(0).getProductName());
    }

    @Test
    void createOrder_shouldAllowRetryAfterFailedOrder_whenStockBecomesAvailable() {
        OrderRequest request = request(
                item(firstProduct.getId(), 2),
                item(secondProduct.getId(), 2)
        );

        assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrder(request, "retry-after-stock-key")
        );
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                Integer.class,
                user.getId()
        ));

        secondProduct.setStock(3);
        productRepository.saveAndFlush(secondProduct);
        OrderResponse retried = orderService.createOrder(request, "retry-after-stock-key");

        assertEquals(new BigDecimal("27.00"), retried.getTotalPrice());
        assertEquals(3, productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                Integer.class,
                user.getId()
        ));
    }

    @Test
    void createOrder_shouldCreateOnlyOneOrder_whenSameKeyArrivesConcurrently() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(() -> concurrentCreate(start));
            Future<Long> second = executor.submit(() -> concurrentCreate(start));
            start.countDown();

            assertEquals(first.get(), second.get());
            assertEquals(4, productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE user_id = ?",
                    Integer.class,
                    user.getId()
            ));
        } finally {
            executor.shutdownNow();
        }
    }

    private Long concurrentCreate(CountDownLatch start) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "", List.of())
        );
        try {
            start.await();
            return orderService.createOrder(
                    request(item(firstProduct.getId(), 1)), "concurrent-key"
            ).getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private OrderRequest request(OrderItemRequest... items) {
        OrderRequest request = new OrderRequest();
        request.setItems(List.of(items));
        return request;
    }

    private OrderItemRequest item(Long productId, int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
