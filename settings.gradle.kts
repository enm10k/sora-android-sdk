include(":sora-android-sdk")

val dirFile = file("./include_app_dir.txt")
if (dirFile.exists()) {
    val includeAppDirList = dirFile.readLines()
    for (appDir in includeAppDirList) {
        if (appDir.startsWith("#")) {
            continue
        }

        logger.info("includeAppDir = $appDir")
        File(appDir).walkTopDown().forEach { dir ->
            if (File(dir, "build.gradle").exists()) {
                include(dir.name)
                project(":${dir.name}").projectDir = dir
            }
        }
    }
}
