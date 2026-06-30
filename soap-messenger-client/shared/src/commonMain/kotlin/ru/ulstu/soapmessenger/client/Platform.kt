package ru.ulstu.soapmessenger.client

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform