package com.example.starter.audit

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface AuditChainHeadRepository : JpaRepository<AuditChainHead, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM AuditChainHead h WHERE h.id = 1")
    fun findByIdForUpdate(): AuditChainHead?
}
