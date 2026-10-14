package org.ozafry.nexuspos

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun currentEpochMillis(): Long