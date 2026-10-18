package dev.joyson.aiworkbench.usage.domain

import org.springframework.data.repository.Repository

/** 구현은 Spring Data 가 만든다. */
interface ProviderCallCostRepository : Repository<ProviderCallCost, Long> {

    fun save(cost: ProviderCallCost): ProviderCallCost

    fun existsById(providerCallId: Long): Boolean
}
