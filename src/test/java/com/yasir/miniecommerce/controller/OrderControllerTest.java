package com.yasir.miniecommerce.controller;

import com.yasir.miniecommerce.exception.IdempotencyKeyConflictException;
import com.yasir.miniecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.yasir.miniecommerce.dto.OrderRequest;
import com.yasir.miniecommerce.dto.OrderResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrder_shouldReturn400_whenItemsIsEmpty() throws Exception {

        mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                    "items": []
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void createOrder_shouldReturn400_whenItemsIsMissing() throws Exception {

        mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void createOrder_shouldReturn400_whenQuantityIsZeroOrNegative() throws Exception {

        mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":0}]}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":-1}]}
                                """))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void createOrder_shouldPassIdempotencyKeyToService() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class), eq("checkout-123")))
                .thenReturn(new OrderResponse());

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "checkout-123")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":1}]}
                                """))
                .andExpect(status().isCreated());

        verify(orderService).createOrder(any(OrderRequest.class), eq("checkout-123"));
    }

    @Test
    void createOrder_shouldReturn409_whenSameKeyIsReusedForDifferentContent() throws Exception {
        OrderResponse firstResponse = new OrderResponse();
        firstResponse.setId(42L);

        when(orderService.createOrder(any(OrderRequest.class), eq("conflict-key")))
                .thenAnswer(invocation -> {
                    OrderRequest request = invocation.getArgument(0);
                    int quantity = request.getItems().get(0).getQuantity();

                    if (quantity == 1) {
                        return firstResponse;
                    }

                    throw new IdempotencyKeyConflictException(
                            "Idempotency-Key daha önce farklı bir sipariş içeriğiyle kullanıldı."
                    );
                });

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "conflict-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "conflict-key")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"quantity":2}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Idempotency-Key daha önce farklı bir sipariş içeriğiyle kullanıldı."
                ));

        verify(orderService, times(2))
                .createOrder(any(OrderRequest.class), eq("conflict-key"));
    }
}
