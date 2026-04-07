package com.recomo.remotecontrol.v3dr.data

/**
 * Network profile for different server environments
 * 
 * 4090 GPU Server IPs:
 * - 172.16.31.22    (T8Space LAN)
 * - 192.168.100.100 (ZeroTier VPN)
 * - 192.168.10.110  (RecomoWifi)
 * 
 * Ports:
 * - 8771: V3DR Lake Server
 * - 8085: Service Control API (4090)
 * - 8083: Orin Service Control (separate machine)
 */
data class NetworkProfile(
    val name: String,
    val serverUrl: String,
    val serviceControlUrl: String,  // For starting/stopping server remotely (port 8085)
    val description: String,
    val tag: NetworkTag
)

enum class NetworkTag {
    ZEROTIER,
    T8SPACE,
    RECOMO_WIFI,
    LOCAL,
    CUSTOM
}

object NetworkProfiles {
    // 4090 GPU Server via ZeroTier VPN
    val ZEROTIER = NetworkProfile(
        name = "ZeroTier",
        serverUrl = "http://192.168.100.100:8771",
        serviceControlUrl = "http://192.168.100.100:8085",
        description = "4090 Server via ZeroTier VPN",
        tag = NetworkTag.ZEROTIER
    )
    
    // 4090 GPU Server via T8Space LAN
    val T8SPACE = NetworkProfile(
        name = "T8Space",
        serverUrl = "http://172.16.31.22:8771",
        serviceControlUrl = "http://172.16.31.22:8085",
        description = "4090 Server via T8Space LAN",
        tag = NetworkTag.T8SPACE
    )
    
    // 4090 GPU Server via RecomoWifi
    val RECOMO_WIFI = NetworkProfile(
        name = "RecomoWifi",
        serverUrl = "http://192.168.10.110:8771",
        serviceControlUrl = "http://192.168.10.110:8085",
        description = "4090 Server via RecomoWifi",
        tag = NetworkTag.RECOMO_WIFI
    )
    
    val LOCAL_HOST = NetworkProfile(
        name = "Localhost",
        serverUrl = "http://localhost:8771",
        serviceControlUrl = "http://localhost:8085",
        description = "Local Development Server",
        tag = NetworkTag.LOCAL
    )
    
    val LOCAL_EMULATOR = NetworkProfile(
        name = "Emulator",
        serverUrl = "http://10.0.2.2:8771",
        serviceControlUrl = "http://10.0.2.2:8085",
        description = "Android Emulator (host machine)",
        tag = NetworkTag.LOCAL
    )
    
    fun getAll(): List<NetworkProfile> = listOf(
        ZEROTIER,
        T8SPACE,
        RECOMO_WIFI,
        LOCAL_HOST,
        LOCAL_EMULATOR
    )
    
    fun getByTag(tag: NetworkTag): NetworkProfile? {
        return getAll().firstOrNull { it.tag == tag }
    }
    
    fun getByUrl(url: String): NetworkProfile? {
        return getAll().firstOrNull { it.serverUrl == url }
    }
    
    fun getServiceControlUrl(serverUrl: String): String {
        val profile = getByUrl(serverUrl)
        return profile?.serviceControlUrl ?: serverUrl.replace(":8771", ":8085")
    }
}
