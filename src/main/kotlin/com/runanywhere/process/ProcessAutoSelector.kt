package com.runanywhere.process

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.runanywhere.psi.MethodMetadata

/**
 * 根据当前方法所在模块等信息，自动选择最匹配的 Java 进程。
 */
object ProcessAutoSelector {

    private val LOG = logger<ProcessAutoSelector>()

    /**
     * 从多个运行中的进程中选出最佳匹配。
     *
     * @return 唯一最佳匹配时返回该进程，否则返回 null（需用户手动选择）
     */
    fun selectBest(
        project: Project,
        processes: List<RunningProcess>,
        method: PsiMethod,
        metadata: MethodMetadata
    ): RunningProcess? {
        if (processes.size <= 1) return processes.firstOrNull()

        val sourceModuleName = ModuleUtilCore.findModuleForPsiElement(method)?.name
        val className = metadata.className

        val scored = processes.map { process ->
            val info = RunTargetInfoExtractor.extract(project, process.descriptor)
            val score = ProcessMatchScorer.score(
                sourceModuleName = sourceModuleName,
                className = className,
                runModuleName = info.moduleName,
                mainClassName = info.mainClassName,
                configurationName = info.configurationName,
                displayName = process.displayName
            )
            LOG.info(
                "Process match: pid=${process.pid}, name=${process.displayName}, " +
                    "module=${info.moduleName}, main=${info.mainClassName}, score=$score"
            )
            process to score
        }.sortedByDescending { it.second }

        val best = scored.first()
        val second = scored.getOrNull(1)

        if (best.second <= 0) {
            LOG.info("Process auto-select: no positive score match")
            return null
        }

        // 只有一个进程得分 > 0，直接选中（即使分数不高）
        val positiveCount = scored.count { it.second > 0 }
        if (positiveCount == 1) {
            LOG.info("Process auto-selected (sole match): ${best.first.displayName} (score=${best.second})")
            return best.first
        }

        if (second != null && best.second == second.second) {
            LOG.info("Process auto-select: tie between ${best.first.displayName} and ${second.first.displayName}")
            return null
        }
        if (second != null && best.second - second.second < 20 && best.second < MODULE_MATCH_THRESHOLD) {
            LOG.info(
                "Process auto-select: ambiguous (${best.first.displayName}=${best.second} vs " +
                    "${second.first.displayName}=${second.second})"
            )
            return null
        }

        LOG.info("Process auto-selected: ${best.first.displayName} (pid=${best.first.pid}, score=${best.second})")
        return best.first
    }

    private const val MODULE_MATCH_THRESHOLD = 100
}
