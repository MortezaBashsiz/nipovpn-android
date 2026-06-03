package net.sudoer.nipovpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class NipoProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default",
    val enabled: Boolean = false,
    val config: NipoConfig = NipoConfig()
)

private fun profilesFile(context: Context): File {
    return File(context.filesDir, "profiles.json")
}

fun loadProfiles(context: Context): List<NipoProfile> {
    val file = profilesFile(context)

    if (!file.exists()) {
        return emptyList()
    }

    return try {
        val array = JSONArray(file.readText())
        val result = mutableListOf<NipoProfile>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val cfgObj = obj.optJSONObject("config") ?: JSONObject()
            val defaultCfg = NipoConfig()

            val cfg = NipoConfig(
                token = cfgObj.optString("token", defaultCfg.token),
                fakeUrls = cfgObj.optString("fakeUrls", defaultCfg.fakeUrls),
                methods = cfgObj.optString("methods", defaultCfg.methods),
                endPoints = cfgObj.optString("endPoints", defaultCfg.endPoints),
                timeout = cfgObj.optString("timeout", defaultCfg.timeout),
                pullTimeout = cfgObj.optString("pullTimeout", defaultCfg.pullTimeout),
                tunnelEnable = cfgObj.optBoolean("tunnelEnable", defaultCfg.tunnelEnable),
                connectionReuse = cfgObj.optBoolean("connectionReuse", defaultCfg.connectionReuse),
                tlsEnable = cfgObj.optBoolean("tlsEnable", defaultCfg.tlsEnable),
                logLevel = cfgObj.optString("logLevel", defaultCfg.logLevel),
                listenIp = cfgObj.optString("listenIp", defaultCfg.listenIp),
                listenPort = cfgObj.optString("listenPort", defaultCfg.listenPort),
                serverIp = cfgObj.optString("serverIp", defaultCfg.serverIp),
                serverPort = cfgObj.optString("serverPort", defaultCfg.serverPort),
                userAgent = cfgObj.optString("userAgent", defaultCfg.userAgent)
            ).normalized()

            result.add(
                NipoProfile(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Profile ${i + 1}"),
                    enabled = obj.optBoolean("enabled", false),
                    config = cfg
                )
            )
        }

        result
    } catch (e: Exception) {
        LogManager.append("Failed to load profiles: ${e.message}")
        emptyList()
    }
}

fun saveProfiles(context: Context, profiles: List<NipoProfile>) {
    val array = JSONArray()

    profiles.forEach { profile ->
        val cfg = profile.config.normalized()

        val cfgObj = JSONObject()
            .put("token", cfg.token)
            .put("fakeUrls", cfg.fakeUrls)
            .put("methods", cfg.methods)
            .put("endPoints", cfg.endPoints)
            .put("timeout", cfg.timeout)
            .put("pullTimeout", cfg.pullTimeout)
            .put("tunnelEnable", cfg.tunnelEnable)
            .put("connectionReuse", cfg.connectionReuse)
            .put("tlsEnable", cfg.tlsEnable)
            .put("logLevel", cfg.logLevel)
            .put("listenIp", cfg.listenIp)
            .put("listenPort", cfg.listenPort)
            .put("serverIp", cfg.serverIp)
            .put("serverPort", cfg.serverPort)
            .put("userAgent", cfg.userAgent)

        val obj = JSONObject()
            .put("id", profile.id)
            .put("name", profile.name)
            .put("enabled", profile.enabled)
            .put("config", cfgObj)

        array.put(obj)
    }

    profilesFile(context).writeText(array.toString(2))
}

fun loadActiveProfile(context: Context): NipoProfile? {
    val profiles = loadProfiles(context)
    return profiles.firstOrNull { profile -> profile.enabled } ?: profiles.firstOrNull()
}
