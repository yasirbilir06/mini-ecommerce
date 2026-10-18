package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.OrderItemRequest;
import com.yasir.miniecommerce.dto.OrderRequest;
import com.yasir.miniecommerce.dto.UpdateOrderStatusRequest;
import com.yasir.miniecommerce.exception.InsufficientStockException;
import com.yasir.miniecommerce.exception.ProductNotFoundException;
import com.yasir.miniecommerce.mapper.OrderMapper;
import com.yasir.miniecommerce.model.Product;
import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.OrderRepository;
import com.yasir.miniecommerce.repository.ProductRepository;
import com.yasir.miniecommerce.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yasir.miniecommerce.dto.OrderResponse;
import com.yasir.miniecommerce.model.Order;
import com.yasir.miniecommerce.model.OrderItem;
import com.yasir.miniecommerce.model.OrderStatus;
import com.yasir.miniecommerce.exception.OrderNotFoundException;
import com.yasir.miniecommerce.exception.InvalidOrderStatusException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Logger log =
            LoggerFactory.getLogger(OrderServiceTest.class);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        log.info("TEST SETUP: Kullanıcı kimliği hazırlanıyor.");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test@example.com",
                        "password"
                )
        );

        log.info("TEST SETUP: Giriş yapan kullanıcı = test@example.com");
    }

    @AfterEach
    void tearDown() {
        log.info("TEST CLEANUP: SecurityContext temizleniyor.");

        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrder_shouldThrowException_whenStockIsInsufficient() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Yetersiz stok nedeniyle sipariş reddedilmeli.");

        // Arrange
        log.info("ADIM 1: Test kullanıcısı oluşturuluyor.");

        User user = new User();
        user.setEmail("test@example.com");

        log.info("Kullanıcı e-postası: {}", user.getEmail());

        log.info("ADIM 2: Ürün oluşturuluyor.");

        Product product = new Product(
                "Keyboard",
                new BigDecimal("50.00"),
                2
        );

        log.info(
                "Ürün: {}, Fiyat: {}, Mevcut stok: {}",
                product.getName(),
                product.getPrice(),
                product.getStock()
        );

        log.info("ADIM 3: Sipariş isteği hazırlanıyor.");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(1L);
        itemRequest.setQuantity(5);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(itemRequest));

        log.info("İstenen ürün ID: {}", itemRequest.getProductId());
        log.info("İstenen miktar: {}", itemRequest.getQuantity());

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(productRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(product));

        log.info("ADIM 4: Mock repository cevapları hazırlandı.");
        log.info("ADIM 5: createOrder() çağrılıyor.");

        // Act & Assert
        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrder(request)
        );

        log.info(
                "ADIM 6: Beklenen exception oluştu: {}",
                exception.getClass().getSimpleName()
        );

        log.info("Exception mesajı: {}", exception.getMessage());

        // Sipariş kaydedilmemeli.
        verify(orderRepository, never()).save(any());

        log.info("ADIM 7: Sipariş kaydedilmedi.");

        // Stok değişmemeli.
        assertEquals(2, product.getStock());

        log.info("ADIM 8: Stok kontrol edildi. Güncel stok: {}", product.getStock());

        // Mapper çağrılmamalı.
        verifyNoInteractions(orderMapper);

        log.info("ADIM 9: OrderMapper çağrılmadı.");

        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void createOrder_shouldThrowException_whenProductDoesNotExist() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Ürün bulunamazsa sipariş oluşturulmamalı.");

        // Arrange
        User user = new User();
        user.setEmail("test@example.com");

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(999L);
        itemRequest.setQuantity(1);

        OrderRequest request = new OrderRequest();
        request.setItems(List.of(itemRequest));

        log.info("ADIM 1: Kullanıcı hazırlandı: {}", user.getEmail());
        log.info("ADIM 2: Aranacak ürün ID: {}", itemRequest.getProductId());

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(productRepository.findByIdWithLock(999L))
                .thenReturn(Optional.empty());

        log.info("ADIM 3: Repository cevapları hazırlandı.");

        // Act & Assert
        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> orderService.createOrder(request)
        );

        log.info("ADIM 4: Beklenen exception oluştu.");
        log.info("Exception mesajı: {}", exception.getMessage());

        // Verify
        verify(productRepository).findByIdWithLock(999L);

        log.info("ADIM 5: Ürün arama işlemi doğrulandı.");

        verify(orderRepository, never()).save(any());

        log.info("ADIM 6: Sipariş kaydedilmedi.");

        verifyNoInteractions(orderMapper);

        log.info("ADIM 7: Mapper çağrılmadı.");

        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void cancelMyOrder_shouldRestoreStock_whenOrderIsCancelled() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Sipariş iptal edildiğinde stok geri yüklenmeli.");

        // Arrange
        User user = new User();
        user.setEmail("test@example.com");

        Product product = new Product(
                "Keyboard",
                new BigDecimal("50.00"),
                3
        );
        ReflectionTestUtils.setField(product, "id", 1L);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("50.00"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setOrderItems(List.of(item));

        item.setOrder(order);

        log.info("ADIM 1: Sipariş hazırlandı.");
        log.info("Ürün: {}", product.getName());
        log.info("Başlangıç stoku: {}", product.getStock());
        log.info("Sipariş edilen miktar: {}", item.getQuantity());
        log.info("Sipariş durumu: {}", order.getStatus());

        when(orderRepository.findByIdAndUserEmailWithLock(
                1L,
                "test@example.com"
        )).thenReturn(Optional.of(order));

        when(productRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(orderMapper.toResponse(any(Order.class)))
                .thenReturn(new OrderResponse());

        log.info("ADIM 2: Mock cevapları hazırlandı.");

        // Act
        OrderResponse response = orderService.cancelMyOrder(1L);

        log.info("ADIM 3: cancelMyOrder() çalıştırıldı.");

        // Assert
        assertEquals(5, product.getStock());

        log.info("ADIM 4: Yeni stok miktarı: {}", product.getStock());

        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        log.info("ADIM 5: Yeni sipariş durumu: {}", order.getStatus());

        assertNotNull(response);

        verify(orderRepository).save(order);
        verify(orderMapper).toResponse(order);
        verify(productRepository).findByIdWithLock(1L);

        log.info("ADIM 6: Sipariş kaydedildi ve mapper çağrıldı.");
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void cancelMyOrder_shouldThrowException_whenOrderIsAlreadyCancelled() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: İptal edilmiş sipariş tekrar iptal edilemez.");

        // Arrange: Test verilerini hazırla
        User user = new User();
        user.setEmail("test@example.com");

        Product product = new Product(
                "Keyboard",
                new BigDecimal("50.00"),
                5
        );

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("50.00"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CANCELLED);
        order.setOrderItems(List.of(item));

        item.setOrder(order);

        log.info("ADIM 1: Sipariş hazırlandı.");
        log.info("Sipariş durumu: {}", order.getStatus());
        log.info("Başlangıç stoku: {}", product.getStock());

        when(orderRepository.findByIdAndUserEmailWithLock(
                1L,
                "test@example.com"
        )).thenReturn(Optional.of(order));

        log.info("ADIM 2: Repository mock'u hazırlandı.");

        // Act + Assert: İptal işlemi hata vermeli
        assertThrows(
                InvalidOrderStatusException.class,
                () -> orderService.cancelMyOrder(1L)
        );

        log.info("ADIM 3: Beklenen hata doğrulandı.");

        // Stok değişmemeli
        assertEquals(5, product.getStock());

        // Sipariş tekrar kaydedilmemeli
        verify(orderRepository, never()).save(any(Order.class));

        // Mapper çağrılmamalı
        verifyNoInteractions(orderMapper);

        log.info("ADIM 4: Stok değişmedi, sipariş kaydedilmedi.");
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void updateOrderStatus_shouldRestoreStock_whenStatusChangesToCancelled() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Sipariş durumu CANCELLED olduğunda stok geri yüklenmeli.");

        // Arrange
        User user = new User();
        user.setEmail("test@example.com");

        Product product = new Product(
                "Keyboard",
                new BigDecimal("50.00"),
                3
        );
        ReflectionTestUtils.setField(product, "id", 1L);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("50.00"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setOrderItems(List.of(item));

        item.setOrder(order);

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);

        log.info("ADIM 1: Test verileri hazırlandı.");
        log.info("Başlangıç stoku: {}", product.getStock());
        log.info("Başlangıç durumu: {}", order.getStatus());

        when(orderRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(order));

        when(productRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(product));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(orderMapper.toResponse(any(Order.class)))
                .thenReturn(new OrderResponse());

        log.info("ADIM 2: Mock cevapları hazırlandı.");

        // Act
        OrderResponse response = orderService.updateOrderStatus(1L, request);

        log.info("ADIM 3: updateOrderStatus() çalıştırıldı.");

        // Assert
        assertEquals(5, product.getStock());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertNotNull(response);

        verify(orderRepository).save(order);
        verify(orderMapper).toResponse(order);
        verify(productRepository).findByIdWithLock(1L);

        log.info("ADIM 4: Yeni stok: {}", product.getStock());
        log.info("ADIM 5: Yeni sipariş durumu: {}", order.getStatus());
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void updateOrderStatus_shouldThrowException_whenDeliveredOrderIsCancelled() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Teslim edilmiş sipariş iptal edilemez.");

        // Arrange
        User user = new User();
        user.setEmail("test@example.com");

        Product product = new Product(
                "Keyboard",
                new BigDecimal("50.00"),
                3
        );

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("50.00"));

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.DELIVERED);
        order.setOrderItems(List.of(item));

        item.setOrder(order);

        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);

        log.info("ADIM 1: Sipariş hazırlandı.");
        log.info("Sipariş durumu: {}", order.getStatus());
        log.info("Başlangıç stoku: {}", product.getStock());

        when(orderRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(order));

        log.info("ADIM 2: Repository mock'u hazırlandı.");

        // Act + Assert
        assertThrows(
                InvalidOrderStatusException.class,
                () -> orderService.updateOrderStatus(1L, request)
        );

        log.info("ADIM 3: Beklenen exception doğrulandı.");

        // Siparişin durumu değişmemeli
        assertEquals(OrderStatus.DELIVERED, order.getStatus());

        // Stok değişmemeli
        assertEquals(3, product.getStock());

        // Sipariş kaydedilmemeli
        verify(orderRepository, never()).save(any(Order.class));

        // Mapper çağrılmamalı
        verifyNoInteractions(orderMapper);

        log.info("ADIM 4: Sipariş durumu ve stok değişmedi.");
        log.info("ADIM 5: Sipariş kaydedilmedi.");
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void updateOrderStatus_shouldThrowException_whenOrderDoesNotExist() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Sipariş bulunamazsa durum güncellenmemeli.");

        // Arrange
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findByIdWithLock(999L))
                .thenReturn(Optional.empty());

        log.info("ADIM 1: Repository sipariş bulunamadı cevabı verecek.");

        // Act + Assert
        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.updateOrderStatus(999L, request)
        );

        log.info("ADIM 2: OrderNotFoundException doğrulandı.");

        // Assert
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(orderMapper);

        log.info("ADIM 3: Sipariş kaydedilmedi, mapper çağrılmadı.");
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void getMyOrders_shouldReturnOrders_whenOrdersExist() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Kullanıcının siparişleri başarıyla getirilmeli.");

        // Arrange
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);

        OrderResponse orderResponse = new OrderResponse();

        when(orderRepository.findByUserEmail("test@example.com"))
                .thenReturn(List.of(order));

        when(orderMapper.toResponse(order))
                .thenReturn(orderResponse);

        log.info("ADIM 1: Repository mock'u hazırlandı.");

        // Act
        List<OrderResponse> responses = orderService.getMyOrders();

        log.info("ADIM 2: getMyOrders() çalıştırıldı.");

        // Assert
        assertEquals(1, responses.size());
        assertSame(orderResponse, responses.get(0));

        verify(orderRepository).findByUserEmail("test@example.com");
        verify(orderMapper).toResponse(order);

        log.info("ADIM 3: Dönen sipariş sayısı: {}", responses.size());
        log.info("ADIM 4: Repository ve mapper çağrıları doğrulandı.");
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void getMyOrderById_shouldThrowException_whenOrderDoesNotBelongToUser() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Kullanıcı kendisine ait olmayan siparişe erişememeli.");

        // Arrange
        Long orderId = 999L;
        String userEmail = "test@example.com";

        when(orderRepository.findByIdAndUserEmail(orderId, userEmail))
                .thenReturn(Optional.empty());

        log.info("ADIM 1: Repository sipariş bulunamadı cevabı verecek.");
        log.info("İstenen sipariş ID: {}", orderId);
        log.info("Giriş yapan kullanıcı: {}", userEmail);

        // Act + Assert
        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getMyOrderById(orderId)
        );

        log.info("ADIM 2: OrderNotFoundException doğrulandı.");

        // Assert
        verify(orderRepository).findByIdAndUserEmail(orderId, userEmail);
        verifyNoInteractions(orderMapper);

        log.info("ADIM 3: Mapper çağrılmadı.");
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }
    @Test
    void getMyOrderById_shouldReturnOrder_whenOrderBelongsToUser() {

        log.info("========== TEST BAŞLADI ==========");
        log.info("Senaryo: Kullanıcı kendi siparişini başarıyla getirebilmeli.");

        // Arrange
        Long orderId = 1L;

        User user = new User();
        user.setEmail("test@example.com");

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CONFIRMED);

        OrderResponse expectedResponse = new OrderResponse();

        when(orderRepository.findByIdAndUserEmail(
                orderId,
                "test@example.com"
        )).thenReturn(Optional.of(order));

        when(orderMapper.toResponse(order))
                .thenReturn(expectedResponse);

        log.info("ADIM 1: Sipariş ve mock cevapları hazırlandı.");
        log.info("Sipariş ID: {}", orderId);
        log.info("Kullanıcı: {}", user.getEmail());

        // Act
        OrderResponse actualResponse = orderService.getMyOrderById(orderId);

        log.info("ADIM 2: getMyOrderById() çalıştırıldı.");

        // Assert
        assertNotNull(actualResponse);
        assertSame(expectedResponse, actualResponse);

        verify(orderRepository).findByIdAndUserEmail(
                orderId,
                "test@example.com"
        );

        verify(orderMapper).toResponse(order);

        log.info("ADIM 3: Dönen response doğrulandı.");
        log.info("ADIM 4: Repository ve mapper çağrıları doğrulandı.");
        log.info("========== TEST BAŞARIYLA TAMAMLANDI ==========");
    }

    @Test
    void createOrder_shouldLockProductsInAscendingIdOrder_andKeepRequestLineOrder() {
        User user = new User();
        user.setEmail("test@example.com");

        Product product10 = productWithId(10L, 10);
        Product product20 = productWithId(20L, 10);
        Product product30 = productWithId(30L, 10);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(product10));
        when(productRepository.findByIdWithLock(20L)).thenReturn(Optional.of(product20));
        when(productRepository.findByIdWithLock(30L)).thenReturn(Optional.of(product30));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenReturn(new OrderResponse());

        OrderRequest request = orderRequest(30L, 10L, 20L);

        orderService.createOrder(request);

        InOrder lockOrder = inOrder(productRepository);
        lockOrder.verify(productRepository).findByIdWithLock(10L);
        lockOrder.verify(productRepository).findByIdWithLock(20L);
        lockOrder.verify(productRepository).findByIdWithLock(30L);

        org.mockito.ArgumentCaptor<Order> savedOrder =
                org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedOrder.capture());
        assertEquals(
                List.of(30L, 10L, 20L),
                savedOrder.getValue().getOrderItems().stream()
                        .map(item -> item.getProduct().getId())
                        .toList()
        );
        assertEquals(9, product10.getStock());
        assertEquals(9, product20.getStock());
        assertEquals(9, product30.getStock());
    }

    @Test
    void createOrder_shouldLockDuplicateProductOnce_andKeepSeparateOrderLines() {
        User user = new User();
        user.setEmail("test@example.com");
        Product product = productWithId(10L, 10);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenReturn(new OrderResponse());

        OrderItemRequest firstItem = orderItemRequest(10L, 2);
        OrderItemRequest secondItem = orderItemRequest(10L, 3);
        OrderRequest request = new OrderRequest();
        request.setItems(List.of(firstItem, secondItem));

        orderService.createOrder(request);

        verify(productRepository, times(1)).findByIdWithLock(10L);
        assertEquals(5, product.getStock());

        org.mockito.ArgumentCaptor<Order> savedOrder =
                org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(savedOrder.capture());
        assertEquals(2, savedOrder.getValue().getOrderItems().size());
        assertEquals(
                List.of(2, 3),
                savedOrder.getValue().getOrderItems().stream()
                        .map(OrderItem::getQuantity)
                        .toList()
        );
    }

    @Test
    void cancelMyOrder_shouldLockProductsInAscendingIdOrder_andRestoreStock() {
        User user = new User();
        user.setEmail("test@example.com");

        Product product10 = productWithId(10L, 4);
        Product product20 = productWithId(20L, 5);
        Product product30 = productWithId(30L, 6);
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setOrderItems(List.of(
                orderItem(order, product30, 3),
                orderItem(order, product10, 1),
                orderItem(order, product20, 2)
        ));

        when(orderRepository.findByIdAndUserEmailWithLock(1L, "test@example.com"))
                .thenReturn(Optional.of(order));
        when(productRepository.findByIdWithLock(10L)).thenReturn(Optional.of(product10));
        when(productRepository.findByIdWithLock(20L)).thenReturn(Optional.of(product20));
        when(productRepository.findByIdWithLock(30L)).thenReturn(Optional.of(product30));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenReturn(new OrderResponse());

        orderService.cancelMyOrder(1L);

        InOrder lockOrder = inOrder(productRepository);
        lockOrder.verify(productRepository).findByIdWithLock(10L);
        lockOrder.verify(productRepository).findByIdWithLock(20L);
        lockOrder.verify(productRepository).findByIdWithLock(30L);
        assertEquals(5, product10.getStock());
        assertEquals(7, product20.getStock());
        assertEquals(9, product30.getStock());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    private Product productWithId(Long id, int stock) {
        Product product = new Product("Product " + id, new BigDecimal("10.00"), stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private OrderRequest orderRequest(Long... productIds) {
        OrderRequest request = new OrderRequest();
        request.setItems(java.util.Arrays.stream(productIds)
                .map(id -> orderItemRequest(id, 1))
                .toList());
        return request;
    }

    private OrderItemRequest orderItemRequest(Long productId, int quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private OrderItem orderItem(Order order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(product.getPrice());
        return item;
    }
}
