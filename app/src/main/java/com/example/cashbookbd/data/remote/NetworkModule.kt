package com.example.cashbookbd.data.remote

import com.example.cashbookbd.BuildConfig
import com.example.cashbookbd.data.remote.dto.PermissionDto
import com.example.cashbookbd.data.remote.dto.PermissionDtoDeserializer
import com.google.gson.GsonBuilder
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Builds the singleton Retrofit/OkHttp stack.
 *
 * The base URL comes from [BuildConfig.BASE_URL] (see app/build.gradle.kts),
 * so the emulator, a physical device and production can each point at a
 * different host without code changes.
 */
object NetworkModule {

    /** Host extracted from [BuildConfig.BASE_URL], e.g. "cashbook_api.test". */
    private val BASE_HOST: String = URI(BuildConfig.BASE_URL).host.orEmpty()

    /** IP to resolve [BASE_HOST] to for local dev (empty => use real DNS). */
    private val LOCAL_HOST_IP: String = BuildConfig.LOCAL_HOST_IP

    private const val TIMEOUT_SECONDS = 30L

    /**
     * Builds the shared Retrofit stack. [tokenProvider] is invoked on every
     * request to fetch the current auth token, so a single instance keeps
     * working across login/logout without rebuilding the stack. Create each
     * service interface (e.g. [ApiService], [LedgerApiService]) from the returned
     * Retrofit so they share one OkHttp client.
     */
    fun retrofit(tokenProvider: () -> String?): Retrofit {
        val gson = GsonBuilder()
            .registerTypeAdapterFactory(PhpEmptyArrayAsMapFactory())
            // Permissions arrive as either strings or {id,name,group_name} objects.
            .registerTypeAdapter(PermissionDto::class.java, PermissionDtoDeserializer())
            .create()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(buildOkHttpClient(tokenProvider))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun buildOkHttpClient(tokenProvider: () -> String?): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // The receive action (POST accounts/payment/specific-item) is NOT
            // idempotent — OkHttp must never silently re-send a request whose
            // response was lost, or cash gets debited twice. Disabled globally;
            // GETs recover via the app's explicit refresh instead.
            .retryOnConnectionFailure(false)
            .dns(VhostDns(BASE_HOST, LOCAL_HOST_IP))
            .addInterceptor(AcceptJsonInterceptor)
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(logging)
            .build()
    }

    /** Always ask the API for JSON. */
    private object AcceptJsonInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
                .build()
            return chain.proceed(request)
        }
    }

    /**
     * Resolves the Valet/Herd `.test` vhost (which has no public DNS entry) to a
     * fixed local IP so the emulator/device can reach it. Every other hostname
     * falls through to the system resolver. When [overrideIp] is blank, this is a
     * no-op and normal DNS applies (production).
     */
    private class VhostDns(
        private val vhost: String,
        private val overrideIp: String,
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (overrideIp.isNotBlank() && hostname.equals(vhost, ignoreCase = true)) {
                // Keep the original hostname for the Host header, but connect to overrideIp.
                return listOf(InetAddress.getByName(overrideIp))
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }
}
