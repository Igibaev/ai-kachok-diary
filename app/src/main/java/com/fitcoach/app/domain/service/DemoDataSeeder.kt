package com.fitcoach.app.domain.service

/** Наполнение приложения «живыми» демо-данными для презентаций (3 недели истории) и их очистка. */
interface DemoDataSeeder {
    suspend fun seedThreeWeeks()
    suspend fun clearAllUserData()
}

class NoopDemoDataSeeder : DemoDataSeeder {
    override suspend fun seedThreeWeeks() = Unit
    override suspend fun clearAllUserData() = Unit
}
