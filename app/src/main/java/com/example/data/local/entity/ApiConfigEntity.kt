package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_configs")
data class ApiConfigEntity(
    @PrimaryKey
    val id: String = "default_config",
    val providerName: String = "OpenAI",
    val baseUrl: String = "https://api.openai.com/v1/",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val systemPrompt: String = "You are a helpful, versatile AI assistant.",
    val temperature: Float = 0.7f,
    val isActive: Boolean = true,
    val lastTestedSuccess: Boolean? = null,
    val lastTestedMessage: String? = null
)
