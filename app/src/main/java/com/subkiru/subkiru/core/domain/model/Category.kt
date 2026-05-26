package com.subkiru.subkiru.core.domain.model

data class Category(
    val id: Long,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val sortOrder: Int,
)
