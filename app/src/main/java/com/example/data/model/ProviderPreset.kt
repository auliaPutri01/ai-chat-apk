package com.example.data.model

data class ProviderPreset(
    val id: String,
    val name: String,
    val defaultBaseUrl: String,
    val sampleApiKey: String,
    val models: List<String>,
    val description: String,
    val iconEmoji: String
)

object ProviderPresets {
    val list = listOf(
        ProviderPreset(
            id = "gemini_native",
            name = "Google Gemini & Veo",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            sampleApiKey = "AIzaSy...",
            models = listOf(
                "gemini-3.5-flash",
                "gemini-3.1-pro-preview",
                "gemini-3.1-flash-lite-preview",
                "gemini-3.1-flash-image-preview",
                "veo-3.1-fast-generate-preview",
                "gemini-3.5-transcribe"
            ),
            description = "Chat, Veo 3 Video, Image Edit, Maps Grounding & Transcribe",
            iconEmoji = "✨"
        ),
        ProviderPreset(
            id = "openai",
            name = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1/",
            sampleApiKey = "sk-proj-...",
            models = listOf("gpt-4o-mini", "gpt-4o", "o3-mini", "gpt-3.5-turbo"),
            description = "Resmi OpenAI GPT-4o, GPT-4o-mini, o3",
            iconEmoji = "🟢"
        ),
        ProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1/",
            sampleApiKey = "sk-or-v1-...",
            models = listOf(
                "meta-llama/llama-3.3-70b-instruct:free",
                "google/gemini-2.0-flash-exp:free",
                "anthropic/claude-3.5-sonnet",
                "deepseek/deepseek-r1"
            ),
            description = "Akses ratusan model AI & model gratis",
            iconEmoji = "🌐"
        ),
        ProviderPreset(
            id = "groq",
            name = "Groq",
            defaultBaseUrl = "https://api.groq.io/openai/v1/",
            sampleApiKey = "gsk_...",
            models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it"),
            description = "Inferensi LPU super cepat (500+ token/detik)",
            iconEmoji = "⚡"
        ),
        ProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com/",
            sampleApiKey = "sk-...",
            models = listOf("deepseek-chat", "deepseek-reasoner"),
            description = "DeepSeek V3 & R1 penalaran mutakhir",
            iconEmoji = "🐋"
        ),
        ProviderPreset(
            id = "ollama",
            name = "Ollama (Lokal / LAN)",
            defaultBaseUrl = "http://10.0.2.2:11434/v1/",
            sampleApiKey = "ollama",
            models = listOf("llama3.2", "deepseek-r1:8b", "mistral", "qwen2.5:7b"),
            description = "Model lokal offline di PC / Server LAN",
            iconEmoji = "🦙"
        ),
        ProviderPreset(
            id = "custom",
            name = "Kustom / Self-Hosted",
            defaultBaseUrl = "https://your-custom-llm-endpoint.com/v1/",
            sampleApiKey = "",
            models = listOf("custom-model"),
            description = "Server proxy, vLLM, TGI, atau gateway sendiri",
            iconEmoji = "⚙️"
        )
    )
}
