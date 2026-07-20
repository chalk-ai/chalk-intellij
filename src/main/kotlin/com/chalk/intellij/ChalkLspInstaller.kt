package com.chalk.intellij

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@Service(Service.Level.APP)
class ChalkLspInstaller {
    private val storage = Path.of(PathManager.getSystemPath(), "chalk", "chalk-lsp")
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private val executable = storage.resolve(if (windows) "chalk-lsp.exe" else "chalk-lsp")
    private val metadata = storage.resolve("installed.json")
    private val backup = storage.resolveSibling("${storage.fileName}.old")

    @Synchronized
    fun installedVersion(): String? {
        return try {
            recoverInstalledVersion()
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun ensureInstalled(force: Boolean = false): Path {
        val installedVersion = recoverInstalledVersion()
        if (!force && installedVersion != null) return executable

        val target = target()
        val version = Regex("\"name\"\\s*:\\s*\"[^\"]*/versions/(v\\d[^\"]*)\"")
            .findAll(request("https://artifactregistry.googleapis.com/v1/projects/chalk-prod/locations/us/repositories/chalk-lsp/packages/chalk-lsp/versions?pageSize=1000"))
            .map { it.groupValues[1] }
            .maxWithOrNull(Comparator(::compareVersions))
            ?: error("Could not determine the latest chalk-lsp release")
        val archiveExtension = if (windows) "zip" else "tar.gz"
        val archiveName = "chalk-lsp-$version-$target.$archiveExtension"
        val downloadBase = "https://artifactregistry.googleapis.com/download/v1/projects/chalk-prod/locations/us/repositories/chalk-lsp/files/chalk-lsp:$version:"
        Files.createDirectories(storage.parent)
        val temporary = Files.createTempDirectory(storage.parent, "chalk-lsp-")
        val replacement = temporary.resolve("replacement-server")

        try {
            val archive = temporary.resolve(archiveName)
            download("${downloadBase}${archiveName}:download?alt=media", archive)
            val expected = request("${downloadBase}${archiveName}.sha256:download?alt=media").trim().split(Regex("\\s+"))[0]
            val actual = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)).joinToString("") { "%02x".format(it) }
            check(expected == actual) { "Checksum mismatch for $archiveName" }
            extract(archive, temporary, archiveExtension)

            val extracted = temporary.resolve("chalk-lsp-$version-$target").resolve(executable.fileName)
            Files.createDirectories(replacement)
            val replacementExecutable = replacement.resolve(executable.fileName)
            Files.copy(extracted, replacementExecutable)
            if (!windows) {
                Files.setPosixFilePermissions(replacementExecutable, setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE,
                ))
            }
            Files.writeString(replacement.resolve(metadata.fileName), "{\n  \"version\": \"$version\",\n  \"target\": \"$target\"\n}\n")

            recoverInstalledVersion()
            backup.toFile().deleteRecursively()
            var preservedExisting = false
            try {
                if (Files.exists(storage)) {
                    Files.move(storage, backup, StandardCopyOption.ATOMIC_MOVE)
                    preservedExisting = true
                }
                Files.move(replacement, storage, StandardCopyOption.ATOMIC_MOVE)
            } catch (error: Exception) {
                replacement.toFile().deleteRecursively()
                if (preservedExisting) Files.move(backup, storage, StandardCopyOption.ATOMIC_MOVE)
                throw error
            }
            backup.toFile().deleteRecursively()
            return executable
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

    private fun recoverInstalledVersion(): String? {
        val liveVersion = installedVersion(storage)
        if (liveVersion != null) {
            backup.toFile().deleteRecursively()
            return liveVersion
        }

        val backupVersion = installedVersion(backup) ?: return null
        storage.toFile().deleteRecursively()
        check(!Files.exists(storage)) { "Could not remove the invalid chalk-lsp installation" }
        Files.move(backup, storage, StandardCopyOption.ATOMIC_MOVE)
        check(installedVersion(storage) == backupVersion) { "Could not restore the previous chalk-lsp installation" }
        return backupVersion
    }

    private fun installedVersion(directory: Path): String? {
        return try {
            val executable = directory.resolve(executable.fileName)
            val metadata = directory.resolve(metadata.fileName)
            if (!Files.isRegularFile(executable) || !Files.isRegularFile(metadata)) null
            else Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(Files.readString(metadata))?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun extract(archive: Path, temporary: Path, archiveExtension: String) {
        if (archiveExtension == "zip") {
            ZipInputStream(BufferedInputStream(Files.newInputStream(archive))).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val destination = temporary.resolve(entry.name).normalize()
                    check(destination.startsWith(temporary)) { "Unsafe archive path: ${entry.name}" }
                    if (entry.isDirectory) Files.createDirectories(destination)
                    else {
                        Files.createDirectories(destination.parent)
                        Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        } else {
            TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(Files.newInputStream(archive)))).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val destination = temporary.resolve(entry.name).normalize()
                    check(destination.startsWith(temporary)) { "Unsafe archive path: ${entry.name}" }
                    if (entry.isDirectory) Files.createDirectories(destination)
                    else {
                        Files.createDirectories(destination.parent)
                        Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    private fun target(): String {
        val os = System.getProperty("os.name")
        val architecture = System.getProperty("os.arch")
        return when {
            os.startsWith("Mac") && architecture in setOf("aarch64", "arm64") -> "aarch64-apple-darwin"
            os.startsWith("Mac") && architecture in setOf("x86_64", "amd64") -> "x86_64-apple-darwin"
            os.startsWith("Linux") && architecture in setOf("aarch64", "arm64") -> "aarch64-unknown-linux-gnu"
            os.startsWith("Linux") && architecture in setOf("x86_64", "amd64") -> "x86_64-unknown-linux-gnu"
            os.startsWith("Windows") && architecture in setOf("x86_64", "amd64") -> "x86_64-pc-windows-gnu"
            else -> error("chalk-lsp does not publish a binary for $os/$architecture")
        }
    }

    private fun request(url: String): String {
        val response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(
            HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString()
        )
        check(response.statusCode() == 200) { "Download failed with HTTP ${response.statusCode()}: $url" }
        return response.body()
    }

    private fun download(url: String, destination: Path) {
        val response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(
            HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofFile(destination)
        )
        check(response.statusCode() == 200) { "Download failed with HTTP ${response.statusCode()}: $url" }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.drop(1).split(Regex("[.-]")).map { it.toIntOrNull() ?: 0 }
        val rightParts = right.drop(1).split(Regex("[.-]")).map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val comparison = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return left.compareTo(right)
    }
}
