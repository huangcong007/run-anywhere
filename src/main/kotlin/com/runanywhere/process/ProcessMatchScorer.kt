package com.runanywhere.process

/**
 * 根据源码上下文与运行配置信息，为候选 Java 进程打分。
 */
object ProcessMatchScorer {

    private const val MODULE_MATCH = 100
    private const val MAIN_CLASS_PACKAGE_MATCH = 60
    private const val ROOT_PACKAGE_MATCH = 30
    private const val CONFIG_NAME_MODULE_MATCH = 25
    private const val DISPLAY_NAME_MODULE_MATCH = 15
    private const val PACKAGE_SEGMENT_MATCH = 45

    fun score(
        sourceModuleName: String?,
        className: String,
        runModuleName: String?,
        mainClassName: String?,
        configurationName: String,
        displayName: String
    ): Int {
        var score = 0

        if (!sourceModuleName.isNullOrBlank() && sourceModuleName == runModuleName) {
            score += MODULE_MATCH
        }

        if (!mainClassName.isNullOrBlank()) {
            val mainPackage = mainClassName.substringBeforeLast('.', "")
            val methodPackage = className.substringBeforeLast('.', "")
            if (mainPackage.isNotEmpty() &&
                (methodPackage == mainPackage || methodPackage.startsWith("$mainPackage."))
            ) {
                score += MAIN_CLASS_PACKAGE_MATCH
            }

            val mainRoot = packageRoot(mainClassName)
            val methodRoot = packageRoot(className)
            if (mainRoot.isNotEmpty() && mainRoot == methodRoot) {
                score += ROOT_PACKAGE_MATCH
            }
        }

        if (!sourceModuleName.isNullOrBlank()) {
            val moduleToken = sourceModuleName.lowercase()
            if (configurationName.lowercase().contains(moduleToken)) {
                score += CONFIG_NAME_MODULE_MATCH
            }
            if (displayName.lowercase().contains(moduleToken)) {
                score += DISPLAY_NAME_MODULE_MATCH
            }
        }

        // 从类包名提取业务分段（如 com.maiyawx.admin.service → admin, service），匹配模块/配置名
        // 解决模块名 maiyawx-admin 与包 com.maiyawx.admin.* 无法精确相等的问题
        score += scorePackageSegmentMatch(className, runModuleName, configurationName, displayName)

        return score
    }

    /**
     * 从全限定类名提取包路径中的业务分段。
     * 例：com.maiyawx.admin.service.material.MaterialService → [admin, service, material]
     */
    internal fun packageSegments(className: String): List<String> {
        val parts = className.split('.')
        if (parts.size < 4) return emptyList()
        // 跳过 com.{company}，去掉末尾类名
        return parts.drop(2).dropLast(1).filter { it.isNotBlank() }
    }

    private fun scorePackageSegmentMatch(
        className: String,
        runModuleName: String?,
        configurationName: String,
        displayName: String
    ): Int {
        val segments = packageSegments(className)
        if (segments.isEmpty()) return 0

        var best = 0
        for (segment in segments) {
            val token = segment.lowercase()
            if (token.length < 3) continue // 跳过过短分段，减少误匹配
            val targets = listOfNotNull(
                runModuleName?.lowercase(),
                configurationName.lowercase(),
                displayName.lowercase()
            )
            if (targets.any { it.contains(token) }) {
                best = maxOf(best, PACKAGE_SEGMENT_MATCH)
            }
        }
        return best
    }

    private fun packageRoot(fqn: String): String =
        fqn.split('.').take(2).joinToString(".")
}
