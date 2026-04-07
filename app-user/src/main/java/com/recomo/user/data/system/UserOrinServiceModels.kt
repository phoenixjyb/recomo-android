package com.recomo.user.data.system

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserOrinServiceStatus(
    @SerialName("name")
    val name: String,
    @SerialName("running")
    val running: Boolean,
    @SerialName("pid")
    val pid: Int? = null,
    @SerialName("uptime_seconds")
    val uptimeSeconds: Double? = null,
    @SerialName("port")
    val port: Int? = null
)

@Serializable
data class UserServiceControlResponse(
    @SerialName("success")
    val success: Boolean,
    @SerialName("message")
    val message: String,
    @SerialName("services")
    val services: Map<String, UserOrinServiceStatus> = emptyMap()
)

@Serializable
data class UserOrinRobotIdentity(
    @SerialName("product_family")
    val productFamily: String = "",
    @SerialName("platform_rev")
    val platformRev: String = "",
    @SerialName("robot_variant")
    val robotVariant: String = "",
    @SerialName("hardware_variant")
    val hardwareVariant: String = "",
    @SerialName("robot_unit_id")
    val robotUnitId: String = "",
    @SerialName("robot_sn")
    val robotSn: String = "",
    @SerialName("robot_ssid")
    val robotSsid: String = "",
    @SerialName("local_orin_ip")
    val localOrinIp: String = "",
    @SerialName("zerotier_ip")
    val zerotierIp: String = "",
    @SerialName("site_ip")
    val siteIp: String = "",
    @SerialName("device_id")
    val deviceId: String = "",
    @SerialName("identity_source")
    val identitySource: String = ""
)
