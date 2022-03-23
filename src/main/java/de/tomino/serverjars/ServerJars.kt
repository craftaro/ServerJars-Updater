package de.tomino.serverjars

import com.serverjars.api.JarDetails
import com.serverjars.api.request.AllRequest
import com.serverjars.api.request.JarRequest
import com.serverjars.api.request.LatestRequest
import com.serverjars.api.request.TypesRequest
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.function.Predicate
import kotlin.system.exitProcess


object ServerJars {

    private val WORKING_DIRECTORY = File(".")
    private val CFG_FILE = File(WORKING_DIRECTORY, "serverjars.properties")
    private val CACHE_DIR = File(WORKING_DIRECTORY, "jar")
    private val cfg = Config(CFG_FILE)

    @Throws(IOException::class, NoSuchAlgorithmException::class)

    @JvmStatic
    fun main(args: Array<String>) {

        println("\n  █████╗  █████╗ ███████╗ █████╗ ███╗  ██╗ ██████╗██████╗ ██╗██████╗ ███████╗\n ██╔══██╗██╔══██╗██╔════╝██╔══██╗████╗ ██║██╔════╝██╔══██╗██║██╔══██╗██╔════╝\n ██║  ██║██║  ╚═╝█████╗  ███████║██╔██╗██║╚█████╗ ██████╔╝██║██████╔╝█████╗\n ██║  ██║██║  ██╗██╔══╝  ██╔══██║██║╚████║ ╚═══██╗██╔═══╝ ██║██╔══██╗██╔══╝\n ╚█████╔╝╚█████╔╝███████╗██║  ██║██║ ╚███║██████╔╝██║     ██║██║  ██║███████╗\n  ╚════╝  ╚════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚══╝╚═════╝ ╚═╝     ╚═╝╚═╝  ╚═╝╚══════╝")
        println("\nSearching for updates...")

        var jar: File?

        jar = if (!CFG_FILE.exists()) {
            println(
                    """It looks like this is your first time using the updater. Would you like to create a config file now? [Y/N]
                If you choose 'n' a default config will be created for you instead.
                """.trimIndent()
            )

            val choice = awaitInput(
                    { s: String -> s.equals("y", ignoreCase = true) || s.equals("n", ignoreCase = true) },
                    "Please choose Y or N"
            )
            setupEnv(choice == null || choice.equals("y", ignoreCase = true))
        } else {
            setupEnv(false)
        }

        if (jar == null) {
            println("\nServerJars could not be reached...")
            println("\nAttempting to load last working Jar.")
            jar = findExistingJar()
            if (jar == null) {
                println("\nAll attempts to run failed...")
                exitProcess(1)
            }
            println("\nThe attempt was successful!")
        }
        val vmArgs = ManagementFactory.getRuntimeMXBean().inputArguments.toTypedArray()

        val cmd = arrayOfNulls<String>(vmArgs.size + args.size + 3)
        cmd[0] = javaExecutable
        System.arraycopy(vmArgs, 0, cmd, 1, vmArgs.size)
        cmd[1 + vmArgs.size] = "-jar"
        cmd[2 + vmArgs.size] = jar.absolutePath
        System.arraycopy(args, 0, cmd, 3 + vmArgs.size, args.size)
        try {
            val process = ProcessBuilder(*cmd)
                    .command(*cmd)
                    .inheritIO()
                    .start()
            Runtime.getRuntime().addShutdownHook(Thread { process.destroy() })
            while (process.isAlive) {
                try {
                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        System.err.println("Server unexpectedly exited with code $exitCode")
                    }
                    break
                } catch (ignore: InterruptedException) { TODO() }
            }
        } catch (ex: IOException) {
            System.err.println("Error starting the Minecraft server.")
            ex.printStackTrace()
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun setupEnv(guided: Boolean): File? {
        cfg.load()
        var type = cfg.type
        var version = cfg.version
        if (guided) {
            println("Connecting to ServerJars to find available jar types...\n")
            val typesResponse = TypesRequest().send()
            if (typesResponse.isSuccess) {
                val typeMap = typesResponse.allTypes
                val types: MutableList<String> = ArrayList()
                for (typeList in typeMap.values) {
                    types.addAll(typeList)
                }

                cfg.setJvmArgs("-Xms" + {} + " -Xmx" + {} + " -XX:+UseG1GC -XX:+ParallelRefProcEnabled " +
                        "-XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC " +
                        "-XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 " +
                        "-XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 " +
                        "-XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 " +
                        "-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 " +
                        "-XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 " +
                        "-Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true")

                println("""What server type would you like to use? Available types:""".trimIndent())
                val typeString = StringBuilder()
                var i = 0
                for (t in types) {
                    if (i == 6) {
                        typeString.append("\n")
                        i = 0
                    }
                    typeString.append(t).append(", ")
                    i++
                }
                println(typeString.substring(0, typeString.length - 2) + ".")
                var chosenJar = awaitInput(
                        { s: String -> types.contains(s.lowercase(Locale.getDefault())) },
                        "The jar type '%s' was not listed above in the type list\nPlease choose another."
                )
                if (chosenJar == null) {
                    chosenJar = "paper"
                    println("Unable to get user input -> defaulting to paper.")
                }
                type = chosenJar
                println("""What server version would you like to run?Leave this blank or type 'latest' for latest""".trimIndent())
                var chosenVersion = awaitInput({ true }, "Hmm.. that version was somehow incorrect...")
                if (chosenVersion != null && chosenVersion.isEmpty()) {
                    chosenVersion = "latest"
                }
                if (chosenVersion == null) {
                    chosenVersion = "latest"
                    println("Unable to get user input -> defaulting to latest.")
                }
                version = chosenVersion
                println("Setup completed!\n")
                cfg.type = type
                cfg.version = version
                try {
                    cfg.save()
                } catch (e: IOException) {
                    println("Could not save to properties file. Default values will be used...\n")
                }
            } else {
                println("Connection to ServerJars could not be established. Default values will be used...\n")
            }
        }
        var jarDetails: JarDetails? = null
        if (version == "latest") {
            val latestResponse = LatestRequest(type).send()
            jarDetails = latestResponse.latestJar
        } else {
            val allResponse = AllRequest(type).send()
            for (jar in allResponse.jars) {
                if (jar.version.equals(version, ignoreCase = true)) {
                    jarDetails = jar
                }
            }
        }
        Files.createDirectories(CACHE_DIR.toPath())
        val jar = File(CACHE_DIR, jarDetails!!.file)
        val hash = if (jar.exists()) HashMD5[jar.toPath()] else ""
        if (hash.isEmpty() || hash != jarDetails.hash) {
            println(if (hash.isEmpty()) "\nDownloading jar..." else "\nUpdate found, downloading...")
            val cachedFiles =
                    CACHE_DIR.listFiles { _: File?, name: String -> name.lowercase(Locale.getDefault()).endsWith(".jar") }
            if (cachedFiles != null) {
                for (f in cachedFiles) {
                    Files.deleteIfExists(f.toPath())
                }
            }
            val response =
                    JarRequest(type, if (version.equals("latest", ignoreCase = true)) null else version, jar).send()
            if (!response.isSuccess) {
                println("\nThe jar version \"$version\" was not found in our database...")
                return null
            }
            println("\nJar updated successfully.")
        } else {
            println("\nThe jar is up to date.")
        }
        val launching = "\n" + """Launching ${jarDetails.file}...""".trimIndent() + "\n\n"
        println("""$launching${launching.replace("[^.]".toRegex(), ".")}""".trimIndent() + "\n")
        return jar
    }

    private fun findExistingJar(): File? {
        val files = File("jar")
                .listFiles { _: File?, name: String -> name.lowercase(Locale.getDefault()).endsWith(".jar") }
        return files?.get(0)
    }

    private val javaExecutable: String
        get() {
            val binDir = File(System.getProperty("java.home"), "bin")
            var javaExe = File(binDir, "java")
            if (!javaExe.exists()) {
                javaExe = File(binDir, "java.exe")
            }
            if (!javaExe.exists()) {
                System.err.println("We could not find your java executable inside '" + binDir.absolutePath + "' - Using command 'java' instead")
                return "java"
            }
            return javaExe.absolutePath
        }
}

fun awaitInput(predicate: Predicate<String>, errorMessage: String): String? {
    try {
        val bufferedReader = BufferedReader(InputStreamReader(System.`in`))
        var line: String
        while (bufferedReader.readLine().also { line = it } != null) {
            line = line.trim { it <= ' ' }
            if (predicate.test(line)) {
                return line
            } else {
                System.err.println(String.format(errorMessage, line).trimIndent())
            }
        }
    } catch (ignore: IOException) { TODO() }
    return null
}
