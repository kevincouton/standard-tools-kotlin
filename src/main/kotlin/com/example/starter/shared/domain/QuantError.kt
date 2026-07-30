package com.example.starter.shared.domain

sealed class QuantError(message: String) : RuntimeException(message)

class ProviderNotAvailableException(provider: String) :
    QuantError("Market data provider not available: $provider")

class DataQualityException(message: String) :
    QuantError("Data quality issue: $message")

class InvalidCommandException(message: String) :
    QuantError("Invalid command: $message")
