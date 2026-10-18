package com.yasir.miniecommerce.controller;

import com.yasir.miniecommerce.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        verify(orderService, never()).createOrder(any());
    }

    @Test
    void createOrder_shouldReturn400_whenItemsIsMissing() throws Exception {

        mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).createOrder(any());
    }
}