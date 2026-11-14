package com.workertracking.app.model

data class Worker(
    val id: Long = System.currentTimeMillis(),
    val orderIndex: Int = 1,
    val fullName: String,
    val position: String = "",
    val hourlyRate: Double = 0.0,
    val phone: String = "",
    val hasSep: Boolean = false,
    val sepDetails: String = "",
    val shoeSize: String = "",
    val jacketSize: String = "",
    val pantsSize: String = "",
    val birthYear: Int? = null,
    val isActive: Boolean = true,
    val notes: String = ""
) {
    val age: Int?
        get() = birthYear?.let {
            val currentYear = java.time.Year.now().value
            if (it in 1940..currentYear) currentYear - it else null
        }
}
