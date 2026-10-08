package com.runanywhere.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.content.Content
import com.runanywhere.arthas.ArthasManager
import com.runanywhere.arthas.ArthasResponse
import com.runanywhere.arthas.ArthasResponseParser
import com.runanywhere.arthas.OgnlExpressionBuilder
import com.runanywhere.concurrent.RunAnywhereExecutors
import com.runanywhere.process.ProcessSelectionPopup
import com.runanywhere.process.RunningProcessDetector
import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.MethodMetadataExtractor
import com.runanywhere.service.ArthasSessionService
import com.runanywhere.settings.RunAnywhereSettings
import com.runanywhere.ui.InstancePickDialog
import com.runanywhere.ui.InvocationResult
import com.runanywhere.ui.ParameterInputDialog
import com.runanywhere.ui.RunResultToolWindowFactory
import com.runanywhere.ui.SecurityConfirmDialog

class RunMethodAction : AnAction() {

    companion object {
        private val LOG = logger<RunMethodAction>()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val method = findPsiMethod(e) ?: return

        if (!SecurityConfirmDialog.confirmIfNeeded(project)) return

        val metadata = MethodMetadataExtractor.extract(method)
        if (metadata == null) {
            Messages.showErrorDialog(
                project,
                "无法提取方法元数据。\n请确保该方法位于具有全限定名的类中。",
                "Run Anywhere"
            )
            return
        }
        LOG.info("RunMethod: class=${metadata.className}, method=${metadata.methodName}, " +
            "static=${metadata.isStatic}, bean=${metadata.springBeanName}, params=${metadata.parameters}")

        val ognlArgs = if (metadata.parameters.isEmpty()) {
            emptyList()
        } else {
            val dialog = ParameterInputDialog(project, metadata.parameters)
            if (!dialog.showAndGet()) return
            dialog.getOgnlArgs()
        }
        val execTimeoutMs = RunAnywhereSettings.getExecTimeoutMs()
        val asyncExecution = RunAnywhereSettings.isAsyncExecution()
        val expandLevel = RunAnywhereSettings.getResultExpandLevel()

        val processes = RunningProcessDetector.getRunningProcesses(project)
        ProcessSelectionPopup.selectFromProcesses(
            project, processes, method, metadata
        ) { process ->
            invokeViaArthas(
                project, metadata, ognlArgs, execTimeoutMs, asyncExecution, expandLevel,
                process.pid, process.displayName
            )
        }
    }

    private fun findPsiMethod(e: AnActionEvent): PsiMethod? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement is PsiMethod) return psiElement

        val psiFile: PsiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        val editor: Editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val offset = editor.caretModel.offset
        val elementAt: PsiElement? = psiFile.findElementAt(offset)
        return PsiTreeUtil.getParentOfType(elementAt, PsiMethod::class.java)
    }

    private fun invokeViaArthas(
        project: Project,
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        execTimeoutMs: Long,
        asyncExecution: Boolean,
        expandLevel: Int,
        pid: Long,
        processName: String
    ) {
        val tabTitle = "PID $pid ${metadata.methodName}()"
        val needsInstancePick = !metadata.isStatic && metadata.springBeanName == null && !metadata.isConstructor
        val preliminary = if (needsInstancePick) {
            "正在查询 ${metadata.className} 的实例..."
        } else {
            OgnlExpressionBuilder.buildCommand(metadata, ognlArgs, expandLevel = expandLevel)
        }
        val runningContent = RunResultToolWindowFactory.showRunning(project, tabTitle, preliminary)

        RunAnywhereExecutors.executeMethod {
            if (project.isDisposed) return@executeMethod
            val arthasManager = ArthasSessionService.getInstance(project).arthasManager
            LOG.info("RunMethod: executing in thread ${Thread.currentThread().name}")

            if (!arthasManager.isAttached(pid)) {
                val attached = arthasManager.attach(pid)
                if (!attached) {
                    showError(
                        project,
                        "Arthas attach 到 PID $pid ($processName) 失败。\n" +
                            "可能原因：\n" +
                            "1. 目标 JVM 不允许 attach\n" +
                            "2. Arthas 启动超时\n" +
                            "3. HTTP 端口被占用\n\n" +
                            "请查看 idea.log 获取详细日志（搜索 [Arthas]）。"
                    )
                    val failResponse = ArthasResponse(-1, "", false, "Arthas attach 失败")
                    RunResultToolWindowFactory.updateResult(
                        project, runningContent,
                        InvocationResult(tabTitle, preliminary, failResponse)
                    )
                    return@executeMethod
                }
            }

            val instanceIndex = if (!needsInstancePick) {
                0
            } else {
                resolveInstanceIndex(project, arthasManager, metadata, pid, execTimeoutMs, tabTitle, runningContent)
                    ?: return@executeMethod
            }
            val command = OgnlExpressionBuilder.buildCommand(
                metadata, ognlArgs, instanceIndex, expandLevel
            )
            LOG.info("RunMethod: executing command: $command (async=$asyncExecution, timeout=${execTimeoutMs}ms)")
            val response = arthasManager.exec(pid, command, execTimeoutMs, asyncExecution)
            LOG.info("RunMethod: response success=${response.isSuccess}, status=${response.statusCode}")
            val finalResponse = if (asyncExecution && ArthasResponseParser.isAsyncSubmitted(response)) {
                RunResultToolWindowFactory.updateResult(
                    project, runningContent,
                    InvocationResult(tabTitle, command, response, asyncMode = true)
                )
                arthasManager.awaitAsyncResult(pid, response, execTimeoutMs) { !project.isDisposed }
            } else {
                response
            }
            val stillSubmitted = ArthasResponseParser.isAsyncSubmitted(finalResponse) &&
                !ArthasResponseParser.hasExecutionOutput(finalResponse)

            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(
                    tabTitle,
                    command,
                    finalResponse,
                    asyncMode = asyncExecution && stillSubmitted
                )
            )
        }
    }

    /**
     * @return 选中的 instances 下标；查询失败或用户取消时返回 null，并写回当前 Tab
     */
    private fun resolveInstanceIndex(
        project: Project,
        arthasManager: ArthasManager,
        metadata: MethodMetadata,
        pid: Long,
        execTimeoutMs: Long,
        tabTitle: String,
        runningContent: Content?
    ): Int? {
        val countCommand = OgnlExpressionBuilder.buildInstanceCountCommand(metadata.className)
        val countResponse = arthasManager.exec(pid, countCommand, execTimeoutMs, async = false)
        val count = ArthasResponseParser.parseIntValue(countResponse)
        if (count == null) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(tabTitle, countCommand, countResponse)
            )
            return null
        }
        if (count <= 0) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(
                    tabTitle,
                    countCommand,
                    ArthasResponse(-1, "", false, "类 ${metadata.className} 在目标 JVM 中没有已创建的实例。")
                )
            )
            return null
        }
        if (count == 1) return 0
        if (project.isDisposed) return null
        val labelCommand = OgnlExpressionBuilder.buildInstanceLabelCommand(metadata.className)
        val labelResponse = arthasManager.exec(pid, labelCommand, execTimeoutMs, async = false)
        val labels = ArthasResponseParser.parseStringItems(labelResponse)
        val display = if (labels.size == count) labels else (0 until count).map { "instances[$it]" }
        var selected: Int? = null
        ApplicationManager.getApplication().invokeAndWait {
            if (!project.isDisposed) {
                selected = InstancePickDialog(project, metadata.className, display).showAndGetIndex()
            }
        }
        if (selected == null) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(
                    tabTitle,
                    countCommand,
                    ArthasResponse(-1, "", false, "已取消选择实例。")
                )
            )
        }
        return selected
    }

    private fun showError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "Run Anywhere")
        }
    }

    override fun update(e: AnActionEvent) {
        val method = findPsiMethod(e)
        e.presentation.isEnabledAndVisible = method != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
