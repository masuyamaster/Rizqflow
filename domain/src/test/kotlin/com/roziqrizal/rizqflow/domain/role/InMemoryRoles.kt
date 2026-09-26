package com.roziqrizal.rizqflow.domain.role

import com.roziqrizal.rizqflow.domain.model.RoomId

/** Penyimpanan tiruan di memori untuk menguji [RoleService] tanpa database. */
class InMemoryRoles : RoleRepository {
    val traders = linkedMapOf<RoomId, TraderProfile>()
    val plans = linkedMapOf<RoomId, DcaPlan>()

    override suspend fun trader(roomId: RoomId): TraderProfile? = traders[roomId]

    override suspend fun saveTrader(profile: TraderProfile) {
        traders[profile.roomId] = profile
    }

    override suspend fun dca(roomId: RoomId): DcaPlan? = plans[roomId]

    override suspend fun saveDca(plan: DcaPlan) {
        plans[plan.roomId] = plan
    }

    override suspend fun allDca(): List<DcaPlan> = plans.values.toList()

    override suspend fun remove(roomId: RoomId) {
        traders.remove(roomId)
        plans.remove(roomId)
    }
}
