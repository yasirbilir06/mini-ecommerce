    package com.yasir.miniecommerce.mapper;

    import com.yasir.miniecommerce.dto.OrderResponse;
    import com.yasir.miniecommerce.model.Order;
    import com.yasir.miniecommerce.model.OrderItem;
    import com.yasir.miniecommerce.model.OrderStatus;
    import com.yasir.miniecommerce.model.Product;
    import com.yasir.miniecommerce.model.User;
    import org.junit.jupiter.api.Test;

    import java.math.BigDecimal;
    import java.util.ArrayList;
    import java.util.List;

    import static org.junit.jupiter.api.Assertions.*;

    class OrderMapperTest {

        private final OrderMapper orderMapper = new OrderMapper();

        @Test
        void toResponse_shouldMapOrderAndItemsCorrectly() {

            // Arrange: Test verilerini hazırla.
            User user = new User();
            user.setEmail("test@example.com");

            Product product = new Product(
                    "Keyboard",
                    new BigDecimal("29.99"),
                    10
            );

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(2);
            item.setPrice(new BigDecimal("29.99"));

            Order order = new Order();
            order.setUser(user);
            order.setTotalPrice(new BigDecimal("59.98"));
            order.setStatus(OrderStatus.PENDING);
            order.getOrderItems().add(item);

            // Act: Mapper'ı çalıştır.
            OrderResponse response = orderMapper.toResponse(order);

            // Assert: Sipariş bilgilerini kontrol et.
            assertEquals("test@example.com", response.getUserEmail());
            assertEquals(
                    new BigDecimal("59.98"),
                    response.getTotalPrice()
            );
            assertEquals(OrderStatus.PENDING, response.getOrderStatus());

            // Sipariş kalemlerini kontrol et.
            assertNotNull(response.getItems());
            assertEquals(1, response.getItems().size());

            assertNull(response.getItems().get(0).getProductId());
            assertEquals(
                    "Keyboard",
                    response.getItems().get(0).getProductName()
            );
            assertEquals(
                    2,
                    response.getItems().get(0).getQuantity()
            );
            assertEquals(
                    new BigDecimal("29.99"),
                    response.getItems().get(0).getPrice()
            );
        }
        @Test
        void toResponse_shouldReturnEmptyItems_whenOrderHasNoItems() {
            // Arrange
            User user = new User();
            user.setEmail("test@example.com");

            Order order = new Order();
            order.setUser(user);
            order.setTotalPrice(BigDecimal.ZERO);
            order.setStatus(OrderStatus.PENDING);
            order.setOrderItems(new ArrayList<>());



            // Act
            OrderResponse response = orderMapper.toResponse(order);

            // Assert
            assertNotNull(response);
            assertNotNull(response.getItems());
            assertTrue(response.getItems().isEmpty());
            assertEquals(BigDecimal.ZERO, response.getTotalPrice());
            assertEquals(OrderStatus.PENDING, response.getOrderStatus());
        }
        @Test
        void toResponse_shouldMapMultipleItems_whenOrderHasMultipleItems() {
            // Arrange
            User user = new User();
            user.setEmail("test@example.com");

            Product product1 = new Product(
                    "Keyboard",
                    new BigDecimal("50.00"),
                    10
            );

            Product product2 = new Product(
                    "Mouse",
                    new BigDecimal("25.00"),
                    20
            );

            OrderItem item1 = new OrderItem();
            item1.setProduct(product1);
            item1.setQuantity(2);
            item1.setPrice(new BigDecimal("50.00"));

            OrderItem item2 = new OrderItem();
            item2.setProduct(product2);
            item2.setQuantity(1);
            item2.setPrice(new BigDecimal("25.00"));

            Order order = new Order();
            order.setUser(user);
            order.setTotalPrice(new BigDecimal("125.00"));
            order.setStatus(OrderStatus.PENDING);
            order.setOrderItems(List.of(item1, item2));


            // Act
            OrderResponse response = orderMapper.toResponse(order);

            // Assert
            assertEquals(2, response.getItems().size());

            assertEquals("Keyboard", response.getItems().get(0).getProductName());
            assertEquals(2, response.getItems().get(0).getQuantity());

            assertEquals("Mouse", response.getItems().get(1).getProductName());
            assertEquals(1, response.getItems().get(1).getQuantity());

            assertEquals(new BigDecimal("125.00"), response.getTotalPrice());
        }
    }