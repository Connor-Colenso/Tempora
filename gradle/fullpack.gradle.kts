/*
 * Full pack test environment.
 *
 * Downloads a GTNH daily build (https://github.com/GTNewHorizons/GTNH-Daily-Builds), swaps this mod's
 * reobfuscated jar into it and launches the whole pack. Mod agnostic: anything built with gtnhconvention can
 * use it via `apply(from = "gradle/fullpack.gradle.kts")`.
 *
 * Tasks:
 *   runFullPackClient       Java 8 client from the mmcprism-java8 zip, launched like runObfClient
 *   runFullPackServer       Java 17+ server from the server-java17 zip, launched via lwjgl3ify-forgePatches
 *   runFullPackServerTests  The server with Horizon-QA, running every @GameTest in the pack (see the end of this file)
 *
 * Both are JavaExec tasks: use --debug-jvm or IntelliJ's Debug action on the Gradle task to attach a debugger.
 *
 * Properties (-P on the command line or in gradle.properties / ~/.gradle/gradle.properties):
 *   fullPack.tag          Daily tag to use, e.g. daily-2026-10-12+784, instead of following the latest.
 *   fullPack.update       Default true: every run checks for a newer daily and moves this side to it, so client
 *                         and server each catch up the next time they run. Saves, configs and options are kept.
 *                         false (or Gradle's --offline) stays on the installed daily; a side installed for the
 *                         first time then joins the other side's daily, so the two can connect. If GitHub can't be
 *                         reached, the installed daily is used. A warning says when the two sides differ.
 *   fullPack.extraMods    Comma separated jar paths to inject as well, e.g. a local build of a dependency.
 *   fullPack.removeMods   Comma separated regexes; pack jars whose file name matches are disabled.
 *   fullPack.dir          Instance root. Default: run/fullpack
 *   fullPack.xmx          Max heap. Default: 6G
 *   fullPack.jvmArgs      Extra JVM args, space separated.
 *   fullPack.serverJava   Java version for the server. Default: 21
 *   fullPack.acceptEula   Default true: installing the server writes eula=true, signifying you accept the Minecraft
 *                         EULA (https://aka.ms/MinecraftEULA). false leaves eula.txt for you to accept by hand.
 *
 * Installing the server for the first time also sets online-mode=false and white-list=false in server.properties, so
 * a dev client (offline, as "Developer") can join. After that, server.properties and eula.txt are yours: later runs
 * and newer dailies leave whatever you set in them.
 *   fullPack.repo         GitHub repo to pull releases from. Default: GTNewHorizons/GTNH-Daily-Builds
 *
 * Pack jars sharing a modid (from mcmod.info) or a file name stem with an injected jar are moved to
 * <instance>/.fullpack/disabled-mods and restored on the next run, so removing an extra mod is reversible.
 * Downloads are cached in ~/.gradle/caches/gtnh-fullpack, shared by every project that uses this script.
 */

import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import com.gtnewhorizons.retrofuturagradle.util.Distribution
import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.zip.ZipFile

abstract class SetupFullPackTask : DefaultTask() {
    @get:Input
    abstract val repo: Property<String>

    /** Regex matched against release asset names to pick the zip for this side. */
    @get:Input
    abstract val assetPattern: Property<String>

    @get:Input
    @get:Optional
    abstract val requestedTag: Property<String>

    /** Whether to move to the latest daily when one comes out, rather than staying on the installed one. */
    @get:Input
    abstract val update: Property<Boolean>

    /** Gradle's --offline: don't ask GitHub for a newer daily. */
    @get:Input
    abstract val offline: Property<Boolean>

    /** Whether a first install of the server writes eula=true, accepting the Minecraft EULA. */
    @get:Input
    abstract val acceptEula: Property<Boolean>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:Internal
    abstract val instanceDir: DirectoryProperty

    /** The other side's instance. A client only connects to a server on the same daily, so the two are kept in step. */
    @get:Internal
    abstract val otherInstanceDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun setup() {
        val instance = instanceDir.get().asFile
        val installed = installedTag(instance)
        val otherInstance = otherInstanceDir.get().asFile
        val otherInstalled = installedTag(otherInstance)

        // Created here rather than held in a field: task fields end up in the configuration cache.
        val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
        val followLatest = update.get() && !offline.get()
        val requested = requestedTag.orNull
        // A pinned daily that's installed, or whose zip is in the cache, needs nothing from GitHub. CI restores the
        // zip from its cache, so a GitHub outage doesn't fail the run.
        val cached = requested?.takeIf { it != installed }?.let { cachedZip(it) }
        val release: Map<*, *>? = when {
            requested != null && (requested == installed || cached != null) -> null
            requested != null -> fetchRelease(http, "tags/" + encode(requested))
            followLatest -> latestOrNull(http, installed)
            installed != null -> null
            // Not following the latest: a first install joins the other side's daily, so the two can connect.
            otherInstalled != null -> fetchRelease(http, "tags/" + encode(otherInstalled))
            else -> fetchRelease(http, "latest")
        }
        val tag = release?.get("tag_name") as String? ?: requested ?: installed!!
        if (tag == installed) {
            // release is null here when GitHub couldn't be asked, so the installed daily may not be the latest.
            val note = if (followLatest && release != null) " (the latest)" else ""
            logger.lifecycle("Full pack: using installed $tag in $instance$note")
            warnIfOtherSideDiffers(tag, otherInstance, otherInstalled, followLatest)
            return
        }

        val zip = cached ?: run {
            val pattern = Regex(assetPattern.get())
            val asset = (release!!["assets"] as List<*>).map { it as Map<*, *> }
                .firstOrNull { pattern.containsMatchIn(it["name"] as String) }
                ?: throw GradleException("Release $tag has no asset matching ${pattern.pattern}")
            download(http, asset)
        }
        if (cached != null) logger.lifecycle("Full pack: using cached ${cached.name}")

        logger.lifecycle("Full pack: installing $tag into $instance")
        val firstInstall = installed == null
        // Mods and libraries are replaced wholesale; everything else is overwritten file by file so saves survive.
        listOf("mods", "libraries", ".fullpack").forEach { File(instance, it).deleteRecursively() }
        extract(zip, instance, keepExisting = !firstInstall)
        if (firstInstall) configureServerForLocalUse(instance)

        val marker = File(instance, ".fullpack/tag")
        marker.parentFile.mkdirs()
        marker.writeText(tag)
        logger.lifecycle("Full pack: $tag ready")
        warnIfOtherSideDiffers(tag, otherInstance, otherInstalled, followLatest)
    }

    private fun installedTag(instance: File): String? =
        File(instance, ".fullpack/tag").takeIf { it.isFile }?.readText()?.trim()

    /**
     * This side's zip for tag if it's in the cache. Asset names hold the tag, e.g.
     * GTNH-daily-2026-11-07+822-server-java17-26.zip. A download is only renamed to its name once its size and
     * checksum are right, so one that's there is whole.
     */
    private fun cachedZip(tag: String): File? {
        val pattern = Regex(assetPattern.get())
        return cacheDir.get().asFile.listFiles()
            ?.firstOrNull { it.isFile && "-$tag-" in it.name && pattern.containsMatchIn(it.name) }
    }

    /**
     * The latest daily. Or, when GitHub can't be reached (no network, rate limited), null to carry on with the
     * installed one: not being able to check for a newer daily shouldn't stop a run.
     */
    private fun latestOrNull(http: HttpClient, installed: String?): Map<*, *>? = try {
        fetchRelease(http, "latest")
    } catch (e: Exception) {
        if (installed == null) throw e
        logger.warn("Full pack: could not check for a newer daily (${e.message}), carrying on with $installed")
        null
    }

    // FML rejects a connection when any mod's version differs, which every daily changes for some mods.
    private fun warnIfOtherSideDiffers(tag: String, otherInstance: File, otherInstalled: String?, followLatest: Boolean) {
        if (otherInstalled == null || otherInstalled == tag) return
        val side = instanceDir.get().asFile.name
        // Daily tags start with their date, so the later one sorts last.
        val newer = maxOf(tag, otherInstalled)
        val olderSide = if (newer == tag) otherInstance.name else side
        if (followLatest && newer == tag) {
            // The other side catches up by itself the next time it runs.
            logger.warn(
                "Full pack: ${otherInstance.name} is still on $otherInstalled, so it can't connect to $side until " +
                    "it next runs and moves to $tag (or run ./gradlew setupFullPack${otherInstance.name}).")
            return
        }
        logger.warn(
            "Full pack: ${otherInstance.name} is on $otherInstalled but $side is on $tag, so they reject each " +
                "other's connection. Put both on one daily, e.g. ./gradlew setupFullPack$olderSide " +
                "-PfullPack.tag=$newer")
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8)

    private fun request(url: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI(url)).header("User-Agent", "gtnh-fullpack-gradle")
        System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() && url.startsWith("https://api.github.com/") }
            ?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    /** Tries a few times on a server error or a dropped connection, GitHub's API gives the odd 502/504. */
    private fun fetchRelease(http: HttpClient, path: String): Map<*, *> {
        val url = "https://api.github.com/repos/${repo.get()}/releases/$path"
        val attempts = 3
        for (attempt in 1..attempts) {
            val failure = try {
                val response = http.send(
                    request(url).header("Accept", "application/vnd.github+json").build(),
                    HttpResponse.BodyHandlers.ofString())
                when {
                    response.statusCode() == 200 -> return JsonSlurper().parseText(response.body()) as Map<*, *>
                    response.statusCode() < 500 || attempt == attempts -> throw GradleException(
                        "GitHub API returned ${response.statusCode()} for $url: ${response.body().take(300)}")
                    else -> "HTTP ${response.statusCode()}"
                }
            } catch (e: java.io.IOException) {
                if (attempt == attempts) throw GradleException("Could not reach $url: ${e.message}", e)
                e.toString()
            }
            logger.warn("Full pack: GitHub API failed ($failure), retrying $url")
            Thread.sleep(5_000L * attempt)
        }
        throw IllegalStateException("unreachable")
    }

    private fun download(http: HttpClient, asset: Map<*, *>): File {
        val name = asset["name"] as String
        val size = (asset["size"] as Number).toLong()
        val sha256 = (asset["digest"] as String?)?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
        val dir = cacheDir.get().asFile.apply { mkdirs() }
        val target = File(dir, name)
        if (target.isFile && target.length() == size) return target

        val part = File(dir, "$name.part")
        logger.lifecycle("Full pack: downloading $name (${size / (1024 * 1024)} MiB)")
        val response = http.send(
            request(asset["browser_download_url"] as String).build(), HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) throw GradleException("Download of $name failed: HTTP ${response.statusCode()}")
        val digest = MessageDigest.getInstance("SHA-256")
        response.body().use { input ->
            part.outputStream().buffered(1 shl 20).use { output ->
                val buffer = ByteArray(1 shl 16)
                var done = 0L
                var lastReported = -1L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    done += read
                    val percent = done * 100 / size
                    if (percent / 10 != lastReported) {
                        lastReported = percent / 10
                        logger.lifecycle("Full pack: $percent%")
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (part.length() != size || (sha256 != null && sha256 != actual)) {
            val got = part.length()
            part.delete()
            throw GradleException("Download of $name is corrupt (size $got/$size, sha256 $actual, expected $sha256)")
        }
        // Only keep the current zip for this side, they are ~700 MiB each.
        val pattern = Regex(assetPattern.get())
        dir.listFiles()?.filter { it.name != name && pattern.containsMatchIn(it.name) }?.forEach { it.delete() }
        part.renameTo(target)
        return target
    }

    private fun extract(zip: File, instance: File, keepExisting: Boolean) {
        val root = instance.canonicalFile
        ZipFile(zip).use { zf ->
            // Prism zips nest the game dir as "<instance name>/.minecraft/", server zips are flat.
            val nested = zf.entries().asSequence().any { "/.minecraft/" in it.name }
            for (entry in zf.entries()) {
                val path = if (nested) entry.name.substringAfter("/.minecraft/", "") else entry.name
                if (path.isEmpty()) continue
                val out = File(root, path).canonicalFile
                if (!out.toPath().startsWith(root.toPath())) throw GradleException("Bad zip entry ${entry.name}")
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                if (keepExisting && out.exists() && out.name in USER_FILES) continue
                out.parentFile.mkdirs()
                zf.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    /** A dev client logs in offline as "Developer", so the server has to accept it. */
    /**
     * First install only: a dev client logs in offline as "Developer" and isn't on any whitelist, and the EULA is
     * accepted if acceptEula. After that these files are the user's, see USER_FILES.
     */
    private fun configureServerForLocalUse(instance: File) {
        val properties = File(instance, "server.properties")
        if (!properties.isFile) return
        properties.writeText(
            properties.readText()
                .replace(Regex("(?m)^online-mode=.*$"), "online-mode=false")
                .replace(Regex("(?m)^white-list=.*$"), "white-list=false"))
        if (acceptEula.get()) File(instance, "eula.txt").writeText("eula=true\n")
    }

    companion object {
        /** Files a player or server admin edits; never overwritten when updating to a new daily. */
        val USER_FILES = setOf(
            "options.txt", "optionsof.txt", "optionsshaders.txt", "servers.dat", "eula.txt", "server.properties",
            "ops.json", "whitelist.json", "banned-ips.json", "banned-players.json", "usercache.json",
            "usernamecache.json")
    }
}

abstract class PrepareFullPackModsTask : DefaultTask() {
    @get:InputFiles
    abstract val injectedMods: ConfigurableFileCollection

    @get:Input
    abstract val removePatterns: ListProperty<String>

    @get:Internal
    abstract val instanceDir: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun prepare() {
        val instance = instanceDir.get().asFile
        val mods = File(instance, "mods")
        val disabled = File(instance, ".fullpack/disabled-mods")
        val injectedList = File(instance, ".fullpack/injected")

        // Undo the previous run so the pack is pristine again.
        if (injectedList.isFile) injectedList.readLines().filter { it.isNotBlank() }.forEach { File(mods, it).delete() }
        disabled.walkBottomUp().filter { it.isFile }.forEach { file ->
            val dest = File(mods, file.relativeTo(disabled).path)
            dest.parentFile.mkdirs()
            file.renameTo(dest)
        }
        disabled.deleteRecursively()

        val jars = injectedMods.files.filter { it.isFile && it.name.endsWith(".jar") }
        if (jars.isEmpty()) throw GradleException("Full pack: no mod jars to inject")
        val modIds = jars.flatMap { modIds(it) }.toSet()
        val stems = jars.map { stem(it.name) }.toSet()
        val patterns = removePatterns.get().map { Regex(it) }

        mods.walkTopDown().filter { it.isFile && it.name.endsWith(".jar") }.toList().forEach { jar ->
            val reason = when {
                patterns.any { it.containsMatchIn(jar.name) } -> "fullPack.removeMods"
                stem(jar.name) in stems -> "same name"
                else -> modIds(jar).intersect(modIds).takeIf { it.isNotEmpty() }?.let { "modid ${it.joinToString()}" }
            } ?: return@forEach
            val dest = File(disabled, jar.relativeTo(mods).path)
            dest.parentFile.mkdirs()
            jar.renameTo(dest)
            logger.lifecycle("Full pack: disabled ${jar.name} ($reason)")
        }

        jars.forEach { jar ->
            jar.copyTo(File(mods, jar.name), overwrite = true)
            logger.lifecycle("Full pack: injected ${jar.name}")
        }
        injectedList.parentFile.mkdirs()
        injectedList.writeText(jars.joinToString("\n") { it.name })
    }

    /** "gregtech-5.09.54.221.jar" -> "gregtech", "CoFHCore-[1.7.10]3.1.4-329.jar" -> "cofhcore" */
    private fun stem(name: String) = name.removeSuffix(".jar").split(Regex("[-_+\\[ ]\\[?\\d")).first().lowercase()

    private fun modIds(jar: File): Set<String> = try {
        ZipFile(jar).use { zf ->
            val info = zf.getEntry("mcmod.info") ?: return emptySet()
            val text = zf.getInputStream(info).bufferedReader().readText()
            Regex("\"modid\"\\s*:\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1].lowercase() }.toSet()
        }
    } catch (e: java.io.IOException) {
        emptySet()
    }
}

fun fullPackProperty(name: String) = providers.gradleProperty("fullPack.$name")

val fullPackDir = layout.projectDirectory.dir(fullPackProperty("dir").getOrElse("run/fullpack"))
val fullPackCache = File(gradle.gradleUserHomeDir, "caches/gtnh-fullpack")
val fullPackXmx = fullPackProperty("xmx").getOrElse("6G")
val fullPackJvmArgs = fullPackProperty("jvmArgs").getOrElse("").split(' ').filter { it.isNotBlank() }
val fullPackGroup = "full pack"

fun registerFullPackSide(side: String, assetPattern: String): TaskProvider<PrepareFullPackModsTask> {
    val instance = fullPackDir.dir(side)
    val setup = tasks.register<SetupFullPackTask>("setupFullPack$side") {
        group = fullPackGroup
        description = "Downloads and installs the GTNH daily $side"
        repo.set(fullPackProperty("repo").orElse("GTNewHorizons/GTNH-Daily-Builds"))
        this.assetPattern.set(assetPattern)
        requestedTag.set(fullPackProperty("tag"))
        update.set(fullPackProperty("update").map { it.toBoolean() }.orElse(true))
        offline.set(gradle.startParameter.isOffline)
        acceptEula.set(fullPackProperty("acceptEula").map { it.toBoolean() }.orElse(true))
        cacheDir.set(fullPackCache)
        instanceDir.set(instance)
        otherInstanceDir.set(fullPackDir.dir(if (side == "Client") "Server" else "Client"))
    }
    return tasks.register<PrepareFullPackModsTask>("prepareFullPack$side") {
        group = fullPackGroup
        description = "Injects this mod into the GTNH daily $side"
        dependsOn(setup)
        injectedMods.from(tasks.named("reobfJar"))
        injectedMods.from(fullPackProperty("extraMods").map { mods -> mods.split(',').filter { it.isNotBlank() } }.orElse(emptyList()))
        removePatterns.set(fullPackProperty("removeMods").map { it.split(',').filter { p -> p.isNotBlank() } }
            .orElse(emptyList()))
        instanceDir.set(instance)
    }
}

val prepareFullPackClient = registerFullPackSide("Client", "-mmcprism-java8\\.zip$")
val prepareFullPackServer = registerFullPackSide("Server", "-server-java17-\\d+\\.zip$")

tasks.register<RunMinecraftTask>("runFullPackClient", Distribution.CLIENT).configure {
    setup(project)
    group = fullPackGroup
    description = "Runs the GTNH daily client (Java 8) with this mod"
    val obfClient = tasks.named<RunMinecraftTask>("runObfClient").get()
    dependsOn(prepareFullPackClient, "downloadVanillaJars", "downloadVanillaAssets")
    workingDir = fullPackDir.dir("Client").asFile
    classpath(obfClient.classpath)
    mainClass.set("net.minecraft.launchwrapper.Launch")
    // Only what a real launcher passes: the pack brings its own mixin/coremod loaders, and the dev
    // environment's extra tweakers and JVM flags (mixin debug export etc.) don't belong here.
    tweakClasses.set(listOf("cpw.mods.fml.common.launcher.FMLTweaker"))
    mcExtExtraRunJvmArguments.set(emptyList())
    extraJvmArgs.set(fullPackJvmArgs)
    maxHeapSize = fullPackXmx
}

/**
 * The server, launched as the pack's own start script does. [extraJvmArgs] come after the pack's, and [freshWorld],
 * if given, is the world folder to start from scratch each run (deleted first) instead of the one server.properties
 * names.
 */
fun registerFullPackServerRun(
    name: String,
    taskDescription: String,
    prepare: TaskProvider<PrepareFullPackModsTask>,
    extraJvmArgs: List<String> = emptyList(),
    freshWorld: String? = null,
) = tasks.register<JavaExec>(name) {
    group = fullPackGroup
    description = taskDescription
    dependsOn(prepare)
    val instance = fullPackDir.dir("Server").asFile
    workingDir = instance
    javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
        languageVersion.set(JavaLanguageVersion.of(fullPackProperty("serverJava").getOrElse("21")))
    })
    // Equivalent of the pack's `java @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui`. JavaExec can't do -jar,
    // the manifest's Class-Path still applies when the jar is on the classpath and Main-Class is read in doFirst.
    val launcherJar = File(instance, "lwjgl3ify-forgePatches.jar")
    classpath(launcherJar)
    mainClass.set("me.eigenraven.lwjgl3ify.rfb.entry.ServerMain")
    minHeapSize = fullPackXmx
    maxHeapSize = fullPackXmx
    jvmArgs("-Dfml.readTimeout=180", "-Duser.language=en", "@" + File(instance, "java9args.txt").absolutePath)
    jvmArgs(fullPackJvmArgs)
    jvmArgs(extraJvmArgs)
    args("nogui")
    // Vanilla's own option, so the server.properties a player set up for the instance is left alone.
    if (freshWorld != null) args("--world", freshWorld)
    standardInput = System.`in`
    doFirst {
        java.util.jar.JarFile(launcherJar).use { jar ->
            jar.manifest?.mainAttributes?.getValue("Main-Class")?.let { mainClass.set(it) }
        }
        // Accepted on first install (see SetupFullPackTask), and left alone after that.
        val eula = File(instance, "eula.txt")
        if (!eula.isFile || !eula.readText().contains(Regex("(?m)^eula=true"))) {
            throw GradleException(
                "Set eula=true in $eula to accept the Minecraft EULA (https://aka.ms/MinecraftEULA)")
        }
        if (freshWorld != null) File(instance, freshWorld).deleteRecursively()
    }
}

registerFullPackServerRun("runFullPackServer", "Runs the GTNH daily server (Java 17+) with this mod", prepareFullPackServer)

/*
 * Horizon-QA (https://github.com/GTNewHorizons/Horizon-QA) in the full pack.
 *
 * runFullPackServerTests injects the Horizon-QA release jar alongside this mod, and runs the server headless in CI
 * mode on a fresh void world (<instance>/horizonqa_world, deleted before each run). Every @GameTest in the pack runs:
 * this mod's, and those the other mods in the pack ship in their jars, so it also checks this mod doesn't break theirs.
 * The server stops itself once they are done, and the task fails if a required test failed.
 *
 * Properties:
 *   fullPack.horizonQaVersion  Horizon-QA release to inject. Default: 0.15.0
 *   fullPack.tests             -Dhorizonqa.tests selector, e.g. a modid or namespace:Class.method, comma separated.
 *                              Default: every test in the pack.
 * Reports (JUnit XML and a status JSON) are written to build/horizonqa-fullpack.
 */
val fullPackHorizonQa = configurations.create("fullPackHorizonQa") {
    isCanBeConsumed = false
    isTransitive = false
    // The release jar (reobfElements), not the -dev jar RFG picks for every other configuration.
    attributes.attribute(
        com.gtnewhorizons.retrofuturagradle.ObfuscationAttribute.OBFUSCATION_ATTRIBUTE,
        com.gtnewhorizons.retrofuturagradle.ObfuscationAttribute.getSrg(objects))
}
dependencies.add(
    fullPackHorizonQa.name,
    "com.github.GTNewHorizons:Horizon-QA:" + fullPackProperty("horizonQaVersion").getOrElse("0.15.0"))

// The same instance as runFullPackServer: each prepare task undoes the last one's injections first.
val prepareFullPackServerTests = tasks.register<PrepareFullPackModsTask>("prepareFullPackServerTests") {
    group = fullPackGroup
    description = "Injects this mod and Horizon-QA into the GTNH daily Server"
    dependsOn("setupFullPackServer")
    injectedMods.from(tasks.named("reobfJar"))
    injectedMods.from(fullPackHorizonQa)
    injectedMods.from(fullPackProperty("extraMods").map { mods -> mods.split(',').filter { it.isNotBlank() } }.orElse(emptyList()))
    removePatterns.set(fullPackProperty("removeMods").map { it.split(',').filter { p -> p.isNotBlank() } }
        .orElse(emptyList()))
    instanceDir.set(fullPackDir.dir("Server"))
}

val fullPackTestReports = layout.buildDirectory.dir("horizonqa-fullpack").get().asFile

registerFullPackServerRun(
    "runFullPackServerTests",
    "Runs every Horizon-QA test in the GTNH daily server (Java 17+) with this mod",
    prepareFullPackServerTests,
    extraJvmArgs = listOfNotNull(
        "-Dhorizonqa.mode=ci",
        "-Dhorizonqa.reportDir=" + fullPackTestReports.absolutePath,
        fullPackProperty("tests").orNull?.let { "-Dhorizonqa.tests=$it" }),
    freshWorld = "horizonqa_world",
).configure {
    // A server that crashes while starting, e.g. on a mixin that fails to apply, still exits 0, having run no test.
    // Horizon-QA writes its status report once the tests are done, so a run without one failed.
    val result = File(fullPackTestReports, "horizonqa-result.json")
    val crashReports = File(fullPackDir.dir("Server").asFile, "crash-reports")
    doFirst { result.delete() }
    doLast {
        if (!result.isFile) {
            throw GradleException(
                "The server stopped before Horizon-QA finished its tests ($result was not written). " +
                    "See the server log above, or the latest crash report in $crashReports")
        }
    }
}
