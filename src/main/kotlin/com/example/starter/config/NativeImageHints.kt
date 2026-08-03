package com.example.starter.config

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import tools.jackson.module.kotlin.KotlinModule
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Configuration
@ImportRuntimeHints(NativeImageHints::class)
class NativeImageHintsConfiguration

class NativeImageHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val loader = classLoader ?: Thread.currentThread().contextClassLoader ?: javaClass.classLoader

        val packagesToScan = listOf(
            "com.example.starter",
            "com.example.starter.grpc",
            "com.example.starter.analysis.grpc",
            "com.example.starter.backtest.grpc",
            "com.example.starter.indicators.grpc",
            "com.example.starter.marketdata.grpc",
            "com.example.starter.metrics.grpc",
            "com.example.starter.portfolio.grpc",
            "com.example.starter.screener.grpc"
        )

        packagesToScan.forEach { pkg ->
            registerPackage(hints, pkg, loader)
        }

        // Jackson Kotlin module
        hints.reflection().registerType(
            KotlinModule::class.java,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS
        )

        // Configuration and migration resources
        hints.resources().registerPattern("application*.yml")
        hints.resources().registerPattern("db/migration/*")
    }

    private fun registerPackage(hints: RuntimeHints, packageName: String, classLoader: ClassLoader) {
        val path = packageName.replace('.', '/')
        val resources = classLoader.getResources(path)
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            try {
                when (url.protocol) {
                    "file" -> scanFilePath(hints, Paths.get(url.toURI()), packageName, classLoader)
                    "jar" -> scanJarUrl(hints, url, packageName, classLoader)
                }
            } catch (ex: Exception) {
                // Ignore unreadable classpath entries
            }
        }
    }

    private fun scanFilePath(
        hints: RuntimeHints,
        basePath: Path,
        packageName: String,
        classLoader: ClassLoader
    ) {
        if (!Files.exists(basePath)) return
        Files.walk(basePath).use { stream ->
            stream.filter { it.toString().endsWith(".class") }
                .map { basePath.relativize(it).toString() }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .map { "$packageName.${it}" }
                .forEach { registerClass(hints, classLoader, it) }
        }
    }

    private fun scanJarUrl(
        hints: RuntimeHints,
        url: java.net.URL,
        packageName: String,
        classLoader: ClassLoader
    ) {
        val urlString = url.toString()
        val separatorIndex = urlString.indexOf("!/")
        val jarUriString = if (separatorIndex > 0) urlString.substring(0, separatorIndex + 2) else urlString
        val jarUri = URI.create(jarUriString)

        FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { fs ->
            Files.walk(fs.getPath("/")).use { stream ->
                stream.filter { it.toString().endsWith(".class") }
                    .map { it.toString().removePrefix("/").removeSuffix(".class").replace('/', '.') }
                    .filter { it.startsWith(packageName) }
                    .forEach { registerClass(hints, classLoader, it) }
            }
        }
    }

    private fun registerClass(hints: RuntimeHints, classLoader: ClassLoader, className: String) {
        try {
            val clazz = Class.forName(className, false, classLoader) ?: return
            val categories = mutableSetOf(
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
            )
            if (clazz.isEnum) {
                categories.add(MemberCategory.PUBLIC_FIELDS)
            }
            hints.reflection().registerType(clazz, *categories.toTypedArray())
        } catch (ex: Throwable) {
            // Skip classes that cannot be loaded during AOT processing
        }
    }
}
