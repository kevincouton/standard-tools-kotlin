package com.example.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KotlinGrpcRestStarterApplication

fun main(args: Array<String>) {
    runApplication<KotlinGrpcRestStarterApplication>(*args)
}
