package com.yasir.miniecommerce;
    //Gerekli paketleri import ediyoruz .
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
    //Spring Boot bu uygulamayı sen yönet diyoruz burada.
@SpringBootApplication
public class MiniEcommerceApplication {
    // Javanın bildiğimiz başlangıç noktası !! Spring Boot uygulamamı başlat diyoruz.
    public static void main(String[] args) {
        SpringApplication.run(MiniEcommerceApplication.class, args);
    }

}
