package com.subkiru.subkiru

import android.app.Application
import android.content.Context
import com.subkiru.subkiru.core.data.db.SubKiruDatabase
import com.subkiru.subkiru.core.data.repository.CategoryRepositoryImpl
import com.subkiru.subkiru.core.data.repository.SettingsRepositoryImpl
import com.subkiru.subkiru.core.data.repository.ServiceTemplateRepositoryImpl
import com.subkiru.subkiru.core.data.repository.SubscriptionRepositoryImpl
import com.subkiru.subkiru.core.domain.repository.CategoryRepository
import com.subkiru.subkiru.core.domain.repository.SettingsRepository
import com.subkiru.subkiru.core.domain.repository.ServiceTemplateRepository
import com.subkiru.subkiru.core.domain.repository.SubscriptionRepository
import com.subkiru.subkiru.core.domain.usecase.AddSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.CalculateMonthlyTotalUseCase
import com.subkiru.subkiru.core.domain.usecase.DeleteSubscriptionUseCase
import com.subkiru.subkiru.core.domain.usecase.GetSubscriptionsUseCase
import com.subkiru.subkiru.core.domain.usecase.GetUpcomingBillingsUseCase
import com.subkiru.subkiru.notification.NotificationHelper
import com.subkiru.subkiru.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock

class SubKiruApplication : Application() {
    val clock: Clock = Clock.systemDefaultZone()

    val database by lazy { SubKiruDatabase.getInstance(this, applicationScope) }

    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepositoryImpl(database.subscriptionDao(), clock)
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    val serviceTemplateRepository: ServiceTemplateRepository by lazy {
        ServiceTemplateRepositoryImpl(database.serviceTemplateDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(this)
    }

    val reminderScheduler by lazy { ReminderScheduler(this) }

    val getSubscriptionsUseCase by lazy { GetSubscriptionsUseCase(subscriptionRepository) }
    val addSubscriptionUseCase by lazy { AddSubscriptionUseCase(subscriptionRepository) }
    val deleteSubscriptionUseCase by lazy { DeleteSubscriptionUseCase(subscriptionRepository) }
    val calculateMonthlyTotalUseCase by lazy { CalculateMonthlyTotalUseCase(subscriptionRepository) }
    val getUpcomingBillingsUseCase by lazy {
        GetUpcomingBillingsUseCase(subscriptionRepository, clock)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        initializeReminder()
    }

    private fun initializeReminder() {
        applicationScope.launch {
            val settings = settingsRepository.observeSettings().first()
            if (settings.isReminderEnabled) {
                reminderScheduler.schedule()
            }
        }
    }

    companion object {
        fun from(context: Context): SubKiruApplication {
            return context.applicationContext as SubKiruApplication
        }
    }
}
