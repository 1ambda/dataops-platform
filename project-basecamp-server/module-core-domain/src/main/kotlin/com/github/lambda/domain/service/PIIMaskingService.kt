package com.github.lambda.domain.service

import com.github.lambda.domain.model.catalog.ColumnInfo
import org.springframework.stereotype.Service

/**
 * PII Masking Service
 *
 * Handles masking of Personally Identifiable Information (PII) in sample data.
 * Columns marked as isPii=true will have their values replaced with "***".
 */
@Service
class PIIMaskingService {
    companion object {
        private val DEFAULT_PII_PATTERNS =
            listOf(
                // Identity
                "email",
                "phone",
                "mobile",
                "ssn",
                "social_security",
                "passport",
                "driver_license",
                "national_id",
                // Name
                "name",
                "first_name",
                "last_name",
                "full_name",
                "username",
                // Address
                "address",
                "street",
                "city",
                "zip",
                "postal",
                "state",
                // Financial
                "credit_card",
                "bank_account",
                "routing",
                "salary",
                "income",
                "payment",
                // Health
                "medical",
                "health",
                "diagnosis",
                "prescription",
                "insurance",
                // Behavioral
                "ip_address",
                "device_id",
                "session_id",
                "cookie",
                "fingerprint",
                // Demographics
                "birth",
                "dob",
                "age",
                "gender",
                "race",
                "ethnicity",
                "religion",
            )

        private const val MASK_VALUE = "***"
    }

    private val piiPatterns: Set<String> = DEFAULT_PII_PATTERNS.map { it.lowercase() }.toSet()

    /**
     * Mask PII columns in sample data
     *
     * @param columns List of column metadata (to identify PII columns)
     * @param sampleData List of sample data rows
     * @return Sample data with PII values replaced with "***"
     */
    fun maskSampleData(
        columns: List<ColumnInfo>,
        sampleData: List<Map<String, Any>>,
    ): List<Map<String, Any>> {
        val piiColumns =
            columns
                .filter { it.isPii }
                .map { it.name }
                .toSet()

        return sampleData.map { row ->
            row.mapValues { (columnName, value) ->
                if (columnName in piiColumns) MASK_VALUE else value
            }
        }
    }

    /**
     * Detect if a column is likely to contain PII based on name patterns
     *
     * @param columnName Column name
     * @param description Optional column description
     * @return true if column is likely to contain PII
     */
    fun detectPII(
        columnName: String,
        description: String?,
    ): Boolean {
        val text = "$columnName ${description ?: ""}".lowercase()
        return piiPatterns.any { pattern ->
            text.contains(pattern) || text.matches(Regex(".*\\b$pattern\\b.*"))
        }
    }

    /**
     * Get list of PII column names from column metadata
     *
     * @param columns List of column metadata
     * @return List of column names that are PII
     */
    fun getPIIColumns(columns: List<ColumnInfo>): List<String> = columns.filter { it.isPii }.map { it.name }
}
