package net.sudoer.nipovpn

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NipoProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = false,
    val config: NipoConfig
)

private const val PREFS_NAME = "nipovpn_profiles"
private const val KEY_PROFILES = "profiles"

fun loadProfiles(context: Context): MutableList<NipoProfile> {

    val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val raw = prefs.getString(KEY_PROFILES, null)
        ?: return mutableListOf(
            NipoProfile(
                name = "Default",
                enabled = true,
                config = NipoConfig()
            )
        )

    val arr = JSONArray(raw)

    val result = mutableListOf<NipoProfile>()

    for (i in 0 until arr.length()) {

        val p = arr.getJSONObject(i)

        val cfg = p.getJSONObject("config")

        result.add(
            NipoProfile(
                id = p.getString("id"),
                name = p.getString("name"),
                enabled = p.getBoolean("enabled"),
                config = NipoConfig(
                    token = cfg.getString("token"),
                    fakeUrls = cfg.getString("fakeUrls"),
                    methods = cfg.getString("methods"),
                    endPoints = cfg.getString("endPoints"),
                    timeout = cfg.getString("timeout"),
                    pullTimeout = cfg.getString("pullTimeout"),
                    tunnelEnable = cfg.getBoolean("tunnelEnable"),
                    connectionReuse = cfg.getBoolean("connectionReuse"),
                    tlsEnable = cfg.getBoolean("tlsEnable"),
                    tlsVerifyPeer = cfg.getBoolean("tlsVerifyPeer"),
                    logLevel = cfg.getString("logLevel"),
                    threads = cfg.getString("threads"),
                    listenIp = cfg.getString("listenIp"),
                    listenPort = cfg.getString("listenPort"),
                    serverIp = cfg.getString("serverIp"),
                    serverPort = cfg.getString("serverPort"),
                    httpVersion = cfg.getString("httpVersion"),
                    userAgent = cfg.getString("userAgent")
                )
            )
        )
    }

    return result
}

fun saveProfiles(
    context: Context,
    profiles: List<NipoProfile>
) {

    val arr = JSONArray()

    profiles.forEach { profile ->

        val cfg = JSONObject()

        cfg.put("token", profile.config.token)
        cfg.put("fakeUrls", profile.config.fakeUrls)
        cfg.put("methods", profile.config.methods)
        cfg.put("endPoints", profile.config.endPoints)
        cfg.put("timeout", profile.config.timeout)
        cfg.put("pullTimeout", profile.config.pullTimeout)
        cfg.put("tunnelEnable", profile.config.tunnelEnable)
        cfg.put("connectionReuse", profile.config.connectionReuse)
        cfg.put("tlsEnable", profile.config.tlsEnable)
        cfg.put("tlsVerifyPeer", profile.config.tlsVerifyPeer)
        cfg.put("logLevel", profile.config.logLevel)
        cfg.put("threads", profile.config.threads)
        cfg.put("listenIp", profile.config.listenIp)
        cfg.put("listenPort", profile.config.listenPort)
        cfg.put("serverIp", profile.config.serverIp)
        cfg.put("serverPort", profile.config.serverPort)
        cfg.put("httpVersion", profile.config.httpVersion)
        cfg.put("userAgent", profile.config.userAgent)

        val obj = JSONObject()

        obj.put("id", profile.id)
        obj.put("name", profile.name)
        obj.put("enabled", profile.enabled)
        obj.put("config", cfg)

        arr.put(obj)
    }

    context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
        .edit()
        .putString(KEY_PROFILES, arr.toString())
        .apply()
}

fun loadActiveProfile(
    context: Context
): NipoProfile? =
    loadProfiles(context)
        .firstOrNull { it.enabled }

fun importProfile(
    uri: String
): NipoProfile {

    val payload = uri.removePrefix("nipovpn://")

    val json = String(
        Base64.decode(
            payload,
            Base64.DEFAULT
        )
    )

    val obj = JSONObject(json)

    val cfg = obj.getJSONObject("config")

    return NipoProfile(
        name = obj.getString("name"),
        enabled = false,
        config = NipoConfig(
            token = cfg.getString("token"),
            fakeUrls = cfg.getString("fakeUrls"),
            methods = cfg.getString("methods"),
            endPoints = cfg.getString("endPoints"),
            timeout = cfg.getString("timeout"),
            pullTimeout = cfg.getString("pullTimeout"),
            tunnelEnable = cfg.getBoolean("tunnelEnable"),
            connectionReuse = cfg.getBoolean("connectionReuse"),
            tlsEnable = cfg.getBoolean("tlsEnable"),
            tlsVerifyPeer = cfg.getBoolean("tlsVerifyPeer"),
            logLevel = cfg.getString("logLevel"),
            threads = cfg.getString("threads"),
            listenIp = cfg.getString("listenIp"),
            listenPort = cfg.getString("listenPort"),
            serverIp = cfg.getString("serverIp"),
            serverPort = cfg.getString("serverPort"),
            httpVersion = cfg.getString("httpVersion"),
            userAgent = cfg.getString("userAgent")
        )
    )
}