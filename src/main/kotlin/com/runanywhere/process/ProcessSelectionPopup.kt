package com.runanywhere.process

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiMethod
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.runanywhere.psi.MethodMetadata
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * 进程选择弹窗。
 */
object ProcessSelectionPopup {

    /**
     * 从已检测到的进程列表中选择目标进程。
     * 多进程时优先根据当前方法所在模块自动匹配，无法唯一确定时再弹窗。
     */
    fun selectFromProcesses(
        project: Project,
        processes: List<RunningProcess>,
        method: PsiMethod? = null,
        metadata: MethodMetadata? = null,
        onSelected: (RunningProcess) -> Unit
    ): Boolean {
        return when {
            processes.isEmpty() -> {
                Messages.showErrorDialog(
                    project,
                    "未检测到运行中的 Java 进程。\n" +
                        "请先运行你的应用程序，然后再调用方法。",
                    "Run Anywhere"
                )
                false
            }
            processes.size == 1 -> {
                onSelected(processes.first())
                true
            }
            else -> {
                val autoSelected = if (method != null && metadata != null) {
                    ProcessAutoSelector.selectBest(project, processes, method, metadata)
                } else {
                    null
                }
                if (autoSelected != null) {
                    onSelected(autoSelected)
                } else {
                    showChooser(project, processes, onSelected)
                }
                true
            }
        }
    }

    fun selectProcess(
        project: Project,
        onSelected: (RunningProcess) -> Unit
    ): Boolean {
        val processes = RunningProcessDetector.getRunningProcesses(project)
        return selectFromProcesses(project, processes, onSelected = onSelected)
    }

    private fun showChooser(
        project: Project,
        processes: List<RunningProcess>,
        onSelected: (RunningProcess) -> Unit
    ) {
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(processes)
            .setTitle("选择目标进程（未能自动匹配）")
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            .setRenderer(object : ColoredListCellRenderer<RunningProcess>() {
                override fun customizeCellRenderer(
                    list: JList<out RunningProcess>,
                    value: RunningProcess,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean
                ) {
                    append("PID ${value.pid}", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ")
                    append(value.displayName, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            })
            .setItemChosenCallback(onSelected)
            .createPopup()
        popup.showCenteredInCurrentWindow(project)
    }
}
