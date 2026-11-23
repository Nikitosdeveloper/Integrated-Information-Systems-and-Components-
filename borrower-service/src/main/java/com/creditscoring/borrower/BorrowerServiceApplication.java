package com.creditscoring.borrower;

import com.creditscoring.common.cache.ProtoCacheConfig;
import com.creditscoring.common.grpc.GrpcServerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Borrower Service: компании-заёмщики и справочник отраслей. */
@SpringBootApplication
@Import({GrpcServerConfig.class, ProtoCacheConfig.class})
public class BorrowerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BorrowerServiceApplication.class, args);
    }
}
