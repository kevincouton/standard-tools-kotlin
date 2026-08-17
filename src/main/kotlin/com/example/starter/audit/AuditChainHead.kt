package com.example.starter.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Singleton row (id = 1) holding the hash of the current audit-chain head.
 * Writers lock this row with SELECT ... FOR UPDATE so concurrent audit
 * writes are serialized at the database level.
 */
@Entity
@Table(name = "audit_chain_head")
class AuditChainHead(
    @Id
    @Column(name = "id", nullable = false)
    var id: Short = 1,

    @Column(name = "head_hash", nullable = false, length = 16)
    var headHash: String = AuditWriter.GENESIS_HASH
)
