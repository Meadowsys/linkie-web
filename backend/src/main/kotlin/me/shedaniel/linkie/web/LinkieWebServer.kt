package me.shedaniel.linkie.web

import guru.zoroark.ratelimit.RateLimit
import guru.zoroark.ratelimit.rateLimited
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.shedaniel.linkie.web.deps.depsCycle
import me.shedaniel.linkie.web.deps.startDepsCycle
import me.shedaniel.linkie.web.deps.startLinkie

@Suppress("ExtractKtorModule")
fun main() {
    depsCycle()
    startDepsCycle()
    startLinkie()
    embeddedServer(Netty, port = 6969) {
        install(CORS) { anyHost() }
        install(IgnoreTrailingSlash)
        install(ContentNegotiation) {
            json(json)
        }
        install(RateLimit) {
            timeBeforeReset = java.time.Duration.ofMinutes(1)
            limit = 75
            limitMessage = """{"message":"You are being rate limited.","retry_after":{{retryAfter}}}"""
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
                cause.printStackTrace()
            }
        }
        routing {
            rateLimited {
                get("/api/versions") {
                    call.respond(getVersions())
                }
                get("/api/versions/{loader}") {
                    val loader = call.parameters["loader"]?.lowercase() ?: throw IllegalArgumentException("No loader specified")
                    if (loader == "all") {
                        call.respond(getAllLoaderVersions())
                    } else {
                        call.respond(getLoaderVersions(loader))
                    }
                }
                get("api/oss") {
                    call.respond(xml.decodeFromString(OssEntries.serializer(), this.javaClass.getResource("/licenses.xml")!!.readText())
                        .oss.map { it.copy(license = it.license.trim()) })
                }
                get("api/namespaces") {
                    call.respond(getNamespaces())
                }
                get("api/search") {
                    val namespaceStr = call.parameters["namespace"]?.lowercase() ?: throw IllegalArgumentException("No namespace specified")
                    val query = call.parameters["query"]
                        ?.replace('.', '/')?.replace('#', '/') ?: throw IllegalArgumentException("No query specified")
                    val version = call.parameters["version"]
                    val limit = call.parameters["limit"]?.toInt() ?: 100
                    val allowClasses = call.parameters["allowClasses"]?.toBoolean() ?: true
                    val allowMethods = call.parameters["allowMethods"]?.toBoolean() ?: true
                    val allowFields = call.parameters["allowFields"]?.toBoolean() ?: true
                    val translateNsStr = call.parameters["translate"]?.lowercase()
                    try {
                        call.respond(search(namespaceStr, translateNsStr, version, query, allowClasses, allowMethods, allowFields, limit))
                    } catch (error: NullPointerException) {
                        call.respond(buildJsonObject {
                            put("error", error.message ?: error.toString())
                        })
                        error.printStackTrace()
                    }
                }
                get("api/source") {
                    val namespaceStr = call.parameters["namespace"]?.lowercase() ?: throw IllegalArgumentException("No namespace specified")
                    val className = call.parameters["class"]?.replace('.', '/') ?: throw IllegalArgumentException("No class specified")
                    val version = call.parameters["version"] ?: throw IllegalArgumentException("No version specified")
                    call.respondText(getSources(namespaceStr, version, className))
                }
            }
        }
    }.start(wait = true)
}
