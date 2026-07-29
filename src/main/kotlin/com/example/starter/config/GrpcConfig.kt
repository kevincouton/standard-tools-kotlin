package com.example.starter.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.service.GrpcService

@Configuration
@ComponentScan(basePackageClasses = [GrpcService::class])
class GrpcConfig
