package com.runanywhere.action

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.content.Content
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

        val processes = RunningProcessDetector.getRunningProcesses(project)
        ProcessSelectionPopup.selectFromProcesses(
            project, processes, method, metadata
        ) { process: RunningProcess ->
            invokeViaArthas(
                project, metadata, ognlArgs, execTimeoutMs, asyncExecution,
                process.pid, process.displayName
            )
        }
    }

    /**
     * F11：动作更新跑在后台线程（[ActionUpdateThread.BGT]），PSI 访问必须持读锁，
     * 否则平台会抛 read-access 断言，导致菜单项异常消失。
     *
     * EDT 上本身具备读权限，直接取值；后台线程走非阻塞读操作的同步执行，
     * 避免 `ReadAction.compute`（2026.3 已废弃）与非阻塞读操作各自的线程约束冲突。
     */
    private fun findPsiMethod(e: AnActionEvent): PsiMethod? {
        return if (ApplicationManager.getApplication().isDispatchThread) {
            extractMethod(e)
        } else {
            ReadAction.nonBlocking<PsiMethod?> { extractMethod(e) }.executeSynchronously()
        }
    }

    private fun extractMethod(e: AnActionEvent): PsiMethod? {
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

    /** 第一阶段产物：可直接用某个实例下标执行，或需要用户先选实例。 */
    private sealed interface PrepareOutcome {
        data class Ready(val instanceIndex: Int) : PrepareOutcome
        data class NeedPick(val labels: List<String>) : PrepareOutcome
    }

    private fun invokeViaArthas(
        project: Project,
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        execTimeoutMs: Long,
        asyncExecution: Boolean,
        pid: Long,
        processName: String,
    ) {
        // P1 C9：标题带上运行配置名，自动匹配进程时也能一眼看出"打到了谁"
        val tabTitle = RunResultToolWindowFactory.uniqueTabTitle(
            project, "$processName · PID $pid ${metadata.methodName}()"
        )
        val needsInstancePick = !metadata.isStatic && metadata.springBeanName == null && !metadata.isConstructor
        val preliminary = if (needsInstancePick) {
            I18n.PROGRESS_QUERY_INSTANCES(metadata.className)
        } else {
            OgnlExpressionBuilder.buildCommand(metadata, ognlArgs)
        }

        // F3：拿不到工具窗口时不再静默返回。先告知用户，再走"无 content"路径，
        // 让 updateResult 在结束阶段自行创建 Tab，结果不会丢。
        val runningContent = RunResultToolWindowFactory.showRunning(project, tabTitle, preliminary)
        if (runningContent == null) {
            RunAnywhereNotifications.error(
                project,
                I18n.EXEC_FAIL_TITLE(),
                I18n.TOOLWINDOW_UNAVAILABLE()
            )
        }

        // 第一阶段：attach + 实例查询。进度与取消只覆盖这一段。
        var outcome: PrepareOutcome? = null
        object : Task.Backgroundable(project, I18n.PROGRESS_TITLE(metadata.methodName), true) {
            override fun run(indicator: ProgressIndicator) {
                outcome = doPrepare(
                    indicator, project, metadata, pid, processName,
                    execTimeoutMs, runningContent, tabTitle, preliminary, needsInstancePick
                )
            }

            override fun onSuccess() {
                val prepared = outcome ?: return
                if (project.isDisposed) return
                // F12：实例选择放在两个后台任务之间，在 EDT 上正常弹窗，
                // 不会出现"模态框盖住进度条"或"后台线程弹模态框"的语义问题。
                val index = when (prepared) {
                    is PrepareOutcome.Ready -> prepared.instanceIndex
                    is PrepareOutcome.NeedPick -> {
                        val picked = InstancePickDialog(project, metadata.className, prepared.labels)
                            .showAndGetIndex()
                        if (picked == null) {
                            writeFailure(
                                project, runningContent, tabTitle, preliminary,
                                I18n.INSTANCE_PICK_CANCELLED()
                            )
                            return
                        }
                        picked
                    }
                }
                startExecute(
                    project, metadata, ognlArgs, execTimeoutMs, asyncExecution,
                    pid, index, runningContent, tabTitle
                )
            }
        }.queue()
    }

    /**
     * 连接目标进程并按需确定实例下标。
     *
     * @return 可继续执行的结果；失败或取消时已把结果写回 Tab 并返回 null。
     */
    private fun doPrepare(
        indicator: ProgressIndicator,
        project: Project,
        metadata: MethodMetadata,
        pid: Long,
        processName: String,
        execTimeoutMs: Long,
        runningContent: Content?,
        tabTitle: String,
        preliminary: String,
        needsInstancePick: Boolean,
    ): PrepareOutcome? {
        if (project.isDisposed) return null
        if (indicator.isCanceled) {
            writeFailure(project, runningContent, tabTitle, preliminary, I18n.EXEC_CANCELLED())
            return null
        }

        indicator.text = I18n.PROGRESS_ATTACH()
        indicator.fraction = 0.05
        val arthasManager = ArthasSessionService.getInstance(project).arthasManager
        LOG.info("RunMethod: preparing in thread ${Thread.currentThread().name}")

        if (!arthasManager.isAttached(pid)) {
            val attached = arthasManager.attach(
                pid,
                onProgress = { fraction ->
                    if (!indicator.isCanceled) indicator.fraction = 0.05 + 0.35 * fraction
                },
                isCanceled = { indicator.isCanceled },
            )
            if (!attached) {
                if (indicator.isCanceled) {
                    writeFailure(project, runningContent, tabTitle, preliminary, I18n.EXEC_CANCELLED())
                    return null
                }
                // P1 D1：通知只说人话（发生了什么），可能原因与日志位置放进结果详情
                RunAnywhereNotifications.error(
                    project,
                    I18n.EXEC_ATTACH_FAIL_TITLE(),
                    I18n.EXEC_ATTACH_FAIL_NOTIFY(pid, processName)
                )
                writeFailure(
                    project, runningContent, tabTitle, preliminary,
                    I18n.EXEC_ATTACH_FAIL_BODY(pid, processName)
                )
                return null
            }
        }

        if (!needsInstancePick) return PrepareOutcome.Ready(0)

        indicator.text = I18n.PROGRESS_QUERY_INSTANCES(metadata.className)
        indicator.fraction = 0.5
        val countCommand = OgnlExpressionBuilder.buildInstanceCountCommand(metadata.className)
        val countResponse = arthasManager.exec(pid, countCommand, execTimeoutMs, async = false)
        if (indicator.isCanceled) {
            writeFailure(project, runningContent, tabTitle, countCommand, I18n.EXEC_CANCELLED())
            return null
        }
        val count = ArthasResponseParser.parseIntValue(countResponse)
        if (count == null) {
            RunResultToolWindowFactory.updateResult(
                project, runningContent, InvocationResult(tabTitle, countCommand, countResponse)
            )
            return null
        }
        if (count <= 0) {
            writeFailure(
                project, runningContent, tabTitle, countCommand,
                I18n.INSTANCE_PICK_NONE(metadata.className)
            )
            return null
        }
        if (count == 1) return PrepareOutcome.Ready(0)

        val labelCommand = OgnlExpressionBuilder.buildInstanceLabelCommand(metadata.className)
        val labelResponse = arthasManager.exec(pid, labelCommand, execTimeoutMs, async = false)
        if (indicator.isCanceled) {
            writeFailure(project, runningContent, tabTitle, countCommand, I18n.EXEC_CANCELLED())
            return null
        }
        val labels = ArthasResponseParser.parseStringItems(labelResponse)
        val display = if (labels.size == count) labels else (0 until count).map { "instances[$it]" }
        return PrepareOutcome.NeedPick(display)
    }

    /** 第二阶段：构造命令并执行。 */
    private fun startExecute(
        project: Project,
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        execTimeoutMs: Long,
        asyncExecution: Boolean,
        pid: Long,
        instanceIndex: Int,
        runningContent: Content?,
        tabTitle: String,
    ) {
        object : Task.Backgroundable(project, I18n.PROGRESS_TITLE(metadata.methodName), true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    doExecute(
                        indicator, project, metadata, ognlArgs, execTimeoutMs,
                        asyncExecution, pid, instanceIndex, runningContent, tabTitle
                    )
                } catch (e: ProcessCanceledException) {
                    writeFailure(
                        project, runningContent, tabTitle,
                        OgnlExpressionBuilder.buildCommand(metadata, ognlArgs, instanceIndex),
                        I18n.EXEC_CANCELLED()
                    )
                }
            }
        }.queue()
    }

    private fun doExecute(
        indicator: ProgressIndicator,
        project: Project,
        metadata: MethodMetadata,
        ognlArgs: List<String>,
        execTimeoutMs: Long,
        asyncExecution: Boolean,
        pid: Long,
        instanceIndex: Int,
        runningContent: Content?,
        tabTitle: String,
    ) {
        if (project.isDisposed) return
        val command = OgnlExpressionBuilder.buildCommand(metadata, ognlArgs, instanceIndex)
        if (indicator.isCanceled) {
            writeFailure(project, runningContent, tabTitle, command, I18n.EXEC_CANCELLED())
            return
        }

        indicator.text = I18n.PROGRESS_EXECUTE(metadata.methodName)
        indicator.fraction = 0.2
        LOG.info("RunMethod: executing command: $command (async=$asyncExecution, timeout=${execTimeoutMs}ms)")

        val arthasManager = ArthasSessionService.getInstance(project).arthasManager
        val response = arthasManager.exec(pid, command, execTimeoutMs, asyncExecution)
        LOG.info("RunMethod: response success=${response.isSuccess}, status=${response.statusCode}")
        indicator.fraction = 0.7
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
            writeFailure(project, runningContent, tabTitle, command, I18n.EXEC_CANCELLED(), asyncMode = asyncExecution)
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
            InvocationResult(tabTitle, command, finalResponse, asyncMode = asyncExecution && isStillAsync)
        )
    }

    /** 统一的失败/取消落盘：写回 Tab，避免用户只看到"执行中"停住。 */
    private fun writeFailure(
        project: Project,
        content: Content?,
        tabTitle: String,
        command: String,
        message: String,
        asyncMode: Boolean = false,
    ) {
        RunResultToolWindowFactory.updateResult(
            project, content,
            InvocationResult(tabTitle, command, ArthasResponse(-1, "", false, message), asyncMode = asyncMode)
        )
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
