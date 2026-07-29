package com.example.starter.testsupport

import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestContainer {
    val instance: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:18").apply {
        withDatabaseName("starter_test")
        withUsername("test")
        withPassword("test")
    }
}
