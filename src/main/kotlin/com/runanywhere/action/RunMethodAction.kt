package com.runanywhere.action

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.content.Content
import com.runanywhere.arthas.ArthasManager
import com.runanywhere.arthas.ArthasResponse
import com.runanywhere.arthas.ArthasResponseParser
import com.runanywhere.arthas.OgnlExpressionBuilder
import com.runanywhere.i18n.I18n
import com.runanywhere.process.ProcessSelectionPopup
import com.runanywhere.process.RunningProcess
import com.runanywhere.process.RunningProcessDetector
import com.runanywhere.psi.MethodMetadata
import com.runanywhere.psi.MethodMetadataExtractor
import com.runanywhere.service.ArthasSessionService
import com.runanywhere.settings.RunAnywhereSettings
import com.runanywhere.ui.InstancePickDialog
import com.runanywhere.ui.InvocationResult
import com.runanywhere.ui.ParameterInputDialog
import com.runanywhere.ui.RunAnywhereNotifications
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
            showHintOrNotification(
                project, e,
                I18n.HINT_METHOD_UNAVAILABLE()
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
                process.pid, process.displayName, process
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

    /**
     * 在编辑器内优先用错误提示（不弹对话框），否则退化为气球通知。
     */
    private fun showHintOrNotification(project: Project, e: AnActionEvent, message: String) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor != null && ApplicationManager.getApplication().isDispatchThread) {
            HintManager.getInstance().showErrorHint(editor, message)
            return
        }
        RunAnywhereNotifications.error(project, I18n.NOTIFY_METHOD_UNAVAILABLE_TITLE(), message)
    }

    private fun invokeViaArthas(
        project: Project,
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        execTimeoutMs: Long,
        asyncExecution: Boolean,
        expandLevel: Int,
        pid: Long,
        processName: String,
        runningProcess: RunningProcess,
    ) {
        // P1 C9：标题带上运行配置名，自动匹配进程时也能一眼看出"打到了谁"
        val tabTitle = RunResultToolWindowFactory.uniqueTabTitle(
            project, "$processName · PID $pid ${metadata.methodName}()"
        )
        val needsInstancePick = !metadata.isStatic && metadata.springBeanName == null && !metadata.isConstructor
        val preliminary = if (needsInstancePick) {
            I18n.PROGRESS_QUERY_INSTANCES(metadata.className)
        } else {
            OgnlExpressionBuilder.buildCommand(metadata, ognlArgs, expandLevel = expandLevel)
        }
        val runningContent = RunResultToolWindowFactory.showRunning(project, tabTitle, preliminary)
            ?: return

        // P1 B6+B7：真实进度 + 可取消。
        // Task.Backgroundable 会在状态栏提供进度条与“取消”按钮，run() 跑在后台线程上，
        // 不再出现“提交任务后 indicator 立刻消失”的问题。
        object : Task.Backgroundable(project, I18n.PROGRESS_TITLE(metadata.methodName), true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    doExecuteMethod(
                        indicator, project, metadata, ognlArgs, execTimeoutMs,
                        asyncExecution, expandLevel, pid, processName, runningContent,
                        tabTitle, preliminary
                    )
                } catch (e: ProcessCanceledException) {
                    RunResultToolWindowFactory.updateResult(
                        project, runningContent,
                        InvocationResult(
                            tabTitle, preliminary,
                            ArthasResponse(-1, "", false, I18n.EXEC_CANCELLED())
                        )
                    )
                }
            }
        }.queue()
    }

    private fun doExecuteMethod(
        indicator: ProgressIndicator,
        project: Project,
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        execTimeoutMs: Long,
        asyncExecution: Boolean,
        expandLevel: Int,
        pid: Long,
        processName: String,
        runningContent: Content?,
        tabTitle: String,
        preliminary: String,
    ) {
        if (project.isDisposed) return
        if (indicator.isCanceled) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(tabTitle, preliminary, ArthasResponse(-1, "", false, I18n.EXEC_CANCELLED()))
            )
            return
        }
        indicator.text = I18n.PROGRESS_ATTACH()
        val arthasManager = ArthasSessionService.getInstance(project).arthasManager
        LOG.info("RunMethod: executing in thread ${Thread.currentThread().name}")

        if (!arthasManager.isAttached(pid)) {
            val attached = arthasManager.attach(
                pid,
                onProgress = { fraction ->
                    if (!indicator.isCanceled) indicator.fraction = 0.3 * fraction
                },
                isCanceled = { indicator.isCanceled },
            )
            if (!attached) {
                if (indicator.isCanceled) {
                    RunResultToolWindowFactory.updateResult(
                        project, runningContent,
                        InvocationResult(tabTitle, preliminary, ArthasResponse(-1, "", false, I18n.EXEC_CANCELLED()))
                    )
                    return
                }
                // P1 D1：通知只说人话（发生了什么），可能原因与日志位置放进结果详情
                RunAnywhereNotifications.error(
                    project,
                    I18n.EXEC_ATTACH_FAIL_TITLE(),
                    I18n.EXEC_ATTACH_FAIL_NOTIFY(pid, processName)
                )
                RunResultToolWindowFactory.updateResult(
                    project, runningContent,
                    InvocationResult(
                        tabTitle, preliminary,
                        ArthasResponse(-1, "", false, I18n.EXEC_ATTACH_FAIL_BODY(pid, processName))
                    )
                )
                return
            }
        }
        indicator.text = I18n.PROGRESS_EXECUTE(metadata.methodName)
        indicator.fraction = 0.5

        val needsInstancePick = !metadata.isStatic && metadata.springBeanName == null && !metadata.isConstructor
        val instanceIndex = if (!needsInstancePick) {
            0
        } else {
            if (indicator.isCanceled) {
                RunResultToolWindowFactory.updateResult(
                    project, runningContent,
                    InvocationResult(tabTitle, OgnlExpressionBuilder.buildCommand(metadata, ognlArgs, 0, expandLevel),
                        ArthasResponse(-1, "", false, I18n.EXEC_CANCELLED()))
                )
                return
            }
            resolveInstanceIndex(project, arthasManager, metadata, pid, execTimeoutMs, tabTitle, runningContent, indicator)
                ?: return
        }
        if (indicator.isCanceled) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(tabTitle, OgnlExpressionBuilder.buildCommand(metadata, ognlArgs, instanceIndex, expandLevel),
                    ArthasResponse(-1, "", false, I18n.EXEC_CANCELLED()))
            )
            return
        }
        val command = OgnlExpressionBuilder.buildCommand(
            metadata, ognlArgs, instanceIndex, expandLevel
        )
        LOG.info("RunMethod: executing command: $command (async=$asyncExecution, timeout=${execTimeoutMs}ms)")
        val response = arthasManager.exec(pid, command, execTimeoutMs, asyncExecution)
        LOG.info("RunMethod: response success=${response.isSuccess}, status=${response.statusCode}")
        indicator.fraction = 0.8
        indicator.text = I18n.PROGRESS_WAIT_RESULT()
        val finalResponse = if (asyncExecution && ArthasResponseParser.isAsyncSubmitted(response)) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(tabTitle, command, response, asyncMode = true)
            )
            arthasManager.awaitAsyncResult(pid, response, execTimeoutMs) {
                !project.isDisposed && !indicator.isCanceled
            }
        } else {
            response
        }
        if (indicator.isCanceled) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent,
                InvocationResult(tabTitle, command,
                    ArthasResponse(-1, "", false, I18n.EXEC_CANCELLED()),
                    asyncMode = asyncExecution)
            )
            return
        }
        val isStillAsync = ArthasResponseParser.isAsyncSubmitted(finalResponse) &&
            !ArthasResponseParser.hasExecutionOutput(finalResponse)
        if (!isStillAsync && !finalResponse.isSuccess) {
            RunAnywhereNotifications.error(
                project,
                I18n.EXEC_FAIL_TITLE(),
                I18n.EXEC_FAIL_BODY(pid, metadata.methodName)
            )
        }
        indicator.fraction = 1.0
        indicator.text = I18n.PROGRESS_DONE()
        RunResultToolWindowFactory.updateResult(
            project, runningContent,
            InvocationResult(
                tabTitle,
                command,
                finalResponse,
                asyncMode = asyncExecution && isStillAsync
            )
        )
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
        runningContent: Content?,
        indicator: ProgressIndicator,
    ): Int? {
        val countCommand = OgnlExpressionBuilder.buildInstanceCountCommand(metadata.className)
        val countResponse = arthasManager.exec(pid, countCommand, execTimeoutMs, async = false)
        if (indicator.isCanceled) return null
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
                    ArthasResponse(-1, "", false, I18n.INSTANCE_PICK_NONE(metadata.className))
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
                    ArthasResponse(-1, "", false, I18n.INSTANCE_PICK_CANCELLED())
                )
            )
        }
        return selected
    }

    override fun update(e: AnActionEvent) {
        val method = findPsiMethod(e)
        e.presentation.isEnabledAndVisible = method != null
        // 菜单文案在运行时从 Bundle 取，plugin.xml 里的 text 仅作注册期占位
        e.presentation.text = I18n.ACTION_TEXT()
        e.presentation.description = I18n.ACTION_DESCRIPTION()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
