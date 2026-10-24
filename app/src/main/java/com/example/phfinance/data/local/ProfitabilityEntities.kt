package com.example.phfinance.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.phfinance.data.model.ApportionmentMethod
import com.example.phfinance.data.model.CostAllocationType
import com.example.phfinance.data.model.TimeActivityType

@Entity(
    tableName = "cost_allocations",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("expenseId"),
        Index("contractId"),
        Index("clientId"),
        Index("serviceId"),
        Index("competence")
    ]
)
data class CostAllocationEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val allocationType: CostAllocationType,
    val contractId: String? = null,
    val clientId: String? = null,
    val serviceId: String? = null,
    val competence: String, // MM/yyyy
    val allocatedAmountCents: Long,
    val allocationPercentage: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "apportionment_policies")
data class ApportionmentPolicyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val policyType: ApportionmentMethod,
    val validFrom: String, // MM/yyyy
    val validTo: String? = null, // MM/yyyy or null
    val targetCategory: String? = null, // null for all indirect costs
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "time_trackings",
    indices = [
        Index("clientId"),
        Index("contractId"),
        Index("serviceId"),
        Index("competence"),
        Index("date")
    ]
)
data class TimeTrackingEntity(
    @PrimaryKey val id: String,
    val clientId: String? = null,
    val contractId: String? = null,
    val serviceId: String? = null,
    val date: Long = System.currentTimeMillis(),
    val competence: String, // MM/yyyy
    val durationMinutes: Int,
    val activityType: TimeActivityType = TimeActivityType.ATENDIMENTO_PRESENCIAL,
    val hourlyEconomicRateCents: Long = 5000L, // R$ 50,00/h economic opportunity cost
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pricing_simulations",
    indices = [Index("serviceId")]
)
data class PricingSimulationScenarioEntity(
    @PrimaryKey val id: String,
    val serviceId: String,
    val scenarioName: String,
    val scenarioType: String = "BASE", // "CONSERVADOR", "BASE", "OTIMISTA"
    val currentPriceCents: Long,
    val proposedPriceCents: Long,
    val currentClientsCount: Int,
    val directCostPerClientCents: Long = 0L,
    val allocatedExpensePerClientCents: Long = 0L,
    val averageDedicatedHoursPerClient: Double = 0.0,
    val hypotheticalChurnPercent: Double = 0.0,
    val targetMarginPercent: Double = 0.0,
    val projectedRevenueCents: Long = 0L,
    val projectedContributionMarginCents: Long = 0L,
    val projectedOperatingResultCents: Long = 0L,
    val breakEvenClientsCount: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
