package com.runanywhere.i18n

import com.intellij.DynamicBundle

/**
 * Run Anywhere 统一文案 Bundle（i18n）。
 *
 * 主语言：中文（与当前用户界面一致）。英文等其它语言资源文件后续补充。
 * 所有用户可见文案通过 [message] 获取，避免散落在各文件里。
 */
object I18n {

    private const val BUNDLE = "messages.RunAnywhereBundle"
    private val BUNDLE_DYNAMIC: DynamicBundle = DynamicBundle(I18n::class.java, BUNDLE)

    // 简单 key（无参数）
    private fun message(key: String): String =
        BUNDLE_DYNAMIC.getMessage(key)

    // 带参数 key。
    // 不用 MessageFormat（DynamicBundle.message(key, params) 内部会用它），
    // 因为文案里含有 JSON 片段和单个 { 字符，会被当成格式占位符而抛异常。
    private fun message(key: String, vararg args: Any?): String {
        var text = BUNDLE_DYNAMIC.getMessage(key)
        args.forEachIndexed { index, arg -> text = text.replace("{$index}", arg.toString()) }
        return text
    }

    // ── 入口动作 ─────────────────────────────────────────
    fun ACTION_TEXT() = message("action.runMethod.text")
    fun ACTION_DESCRIPTION() = message("action.runMethod.description")

    // ── 工具窗口 ─────────────────────────────────────────
    fun TOOL_WINDOW_TITLE() = message("toolWindow.title")
    fun WELCOME_TAB_NAME() = message("toolWindow.welcomeTab")

    // ── 进程选择 ─────────────────────────────────────────
    fun SELECT_PROCESS_TITLE() = message("process.select.title")
    fun SELECT_PROCESS_EMPTY_TITLE() = message("process.empty.title")
    fun SELECT_PROCESS_EMPTY_BODY() = message("process.empty.body")
    fun SELECT_PROCESS_INFO_TITLE() = message("process.info.title")
    fun SELECT_PROCESS_INFO_BODY(pid: Long, name: String) =
        message("process.info.body", pid, name)

    // ── 安全确认 ─────────────────────────────────────────
    fun SECURITY_DIALOG_TITLE() = message("security.dialog.title")
    fun SECURITY_OK_BUTTON() = message("security.ok")
    fun SECURITY_CANCEL_BUTTON() = message("security.cancel")
    fun SECURITY_DONT_ASK() = message("security.dontAsk")

    // ── 参数输入 ─────────────────────────────────────────
    fun PARAM_DIALOG_TITLE() = message("param.dialog.title")
    fun PARAM_OK_BUTTON() = message("param.ok")
    fun PARAM_CANCEL_BUTTON() = message("param.cancel")
    fun PARAM_COLUMN_NAME() = message("param.col.name")
    fun PARAM_COLUMN_TYPE() = message("param.col.type")
    fun PARAM_COLUMN_VALUE() = message("param.col.value")
    fun PARAM_COLUMN_NULL() = message("param.col.null")
    fun PARAM_NULL_ACCESSIBLE_NAME(paramName: String) =
        message("param.null.accessibleName", paramName)
    fun PARAM_EMPTY() = message("param.empty")
    fun PARAM_UNSUPPORTED_TYPE() = message("param.unsupported.type")
    fun PARAM_JSON_HINT() = message("param.json.hint")

    // ── 实例选择 ─────────────────────────────────────────
    fun INSTANCE_PICK_TITLE() = message("instance.pick.title")
    fun INSTANCE_PICK_OK() = message("instance.pick.ok")
    fun INSTANCE_PICK_CANCEL() = message("instance.pick.cancel")
    fun INSTANCE_PICK_LABEL(className: String, count: Int) =
        message("instance.pick.label", className, count)
    fun INSTANCE_PICK_CANCELLED() = message("instance.pick.cancelled")
    fun INSTANCE_PICK_NONE(className: String) =
        message("instance.pick.none", className)

    // ── 设置页 ───────────────────────────────────────────
    fun SETTINGS_NAME() = message("settings.name")
    fun SETTINGS_ASYNC() = message("settings.async")
    fun SETTINGS_ASYNC_TIP() = message("settings.async.tip")
    fun SETTINGS_WARMUP() = message("settings.warmup")
    fun SETTINGS_WARMUP_TIP() = message("settings.warmup.tip")
    fun SETTINGS_TIMEOUT_LABEL() = message("settings.timeout.label")
    fun SETTINGS_TIMEOUT_HINT(min: Int, max: Int) =
        message("settings.timeout.hint", min, max)
    fun SETTINGS_EXPAND_LABEL() = message("settings.expand.label")
    fun SETTINGS_EXPAND_HINT(min: Int, max: Int) =
        message("settings.expand.hint", min, max)
    fun SETTINGS_JSON_LIB_LABEL() = message("settings.jsonLib.label")
    fun SETTINGS_JSON_LIB_HINT() = message("settings.jsonLib.hint")
    fun SETTINGS_RESTORE_SECURITY() = message("settings.restoreSecurity")
    fun SETTINGS_RESTORE_SECURITY_TIP() = message("settings.restoreSecurity.tip")
    fun SETTINGS_ERROR_TIMEOUT(min: Int, max: Int, current: Int) =
        message("settings.error.timeout", min, max, current)
    fun SETTINGS_ERROR_EXPAND(min: Int, max: Int, current: Int) =
        message("settings.error.expand", min, max, current)

    // ── 执行反馈 ─────────────────────────────────────────
    fun EXEC_ATTACH_FAIL_TITLE() = message("exec.attachFail.title")
    fun EXEC_ATTACH_FAIL_BODY(pid: Long, name: String) =
        message("exec.attachFail.body", pid, name, message("exec.attachFail.reasons"))
    fun EXEC_FAIL_TITLE() = message("exec.fail.title")
    fun EXEC_FAIL_BODY(pid: Long, method: String) =
        message("exec.fail.body", pid, method)
    fun EXEC_CANCELLED() = message("exec.cancelled")

    // ── 结果 Tab ─────────────────────────────────────────
    fun TAB_RUNNING_START_AT(now: String) = message("tab.running.startAt", now)
    fun TAB_RUNNING_STATUS() = message("tab.running.status")
    fun TAB_RUNNING_PID(pid: String) = message("tab.running.pid", pid)
    fun TAB_RESULT_TIME(time: String) = message("tab.result.time", time)
    fun TAB_RESULT_STATUS(icon: String, status: String) =
        message("tab.result.status", icon, status)
    fun TAB_RESULT_PID(pid: String) = message("tab.result.pid", pid)
    fun TAB_STATUS_SUCCESS() = message("tab.status.success")
    fun TAB_STATUS_FAIL() = message("tab.status.fail")
    fun TAB_STATUS_ASYNC() = message("tab.status.async")
    fun TAB_COMMAND_HEADER() = message("tab.command.header")
    fun TAB_RAW_HEADER() = message("tab.raw.header")
    fun TAB_ERROR_PREFIX() = message("tab.error.prefix")
    fun TAB_PARSED_HEADER() = message("tab.parsed.header")
    fun TAB_WELCOME_TEXT() = message("tab.welcome")
    fun TAB_COPY_BUTTON() = message("tab.copyButton")
    fun TAB_TOGGLE_DETAILS() = message("tab.toggleDetails")
    fun TAB_COLLAPSE_DETAILS() = message("tab.collapseDetails")

    // ── 读屏播报 ─────────────────────────────────────────
    fun ANNOUNCE_RUNNING(tabTitle: String) = message("announce.running", tabTitle)
    fun ANNOUNCE_RESULT(pid: String, status: String) =
        message("announce.result", pid, status)

    // ── 方法不可用 ──────────────────────────────────────
    fun HINT_METHOD_UNAVAILABLE() = message("hint.methodUnavailable")
    fun NOTIFY_METHOD_UNAVAILABLE_TITLE() = message("notify.methodUnavailable.title")

    // ── 进度提示 ────────────────────────────────────────
    fun PROGRESS_QUERY_INSTANCES(className: String) = message("progress.queryInstances", className)
    fun PROGRESS_TITLE(methodName: String) = message("progress.title", methodName)
    fun PROGRESS_ATTACH() = message("progress.attach")
    fun PROGRESS_EXECUTE(methodName: String) = message("progress.execute", methodName)
    fun PROGRESS_WAIT_RESULT() = message("progress.waitResult")
    fun PROGRESS_DONE() = message("progress.done")

    // ── 执行结果（内联） ────────────────────────────────
    fun EXEC_ATTACH_FAILED_INLINE() = message("exec.attachFailedInline")
    fun EXEC_ASYNC_NO_RESULT(seconds: Long) = message("exec.asyncNoResult", seconds)

    // ── 结果解析文本 ────────────────────────────────────
    fun PARSED_NO_RESULT() = message("parsed.noResult")
    fun PARSED_ASYNC_SUBMITTED() = message("parsed.asyncSubmitted")
    fun PARSED_JOB_ID(jobId: Int) = message("parsed.jobId", jobId)
    fun PARSED_JOB_STATUS(jobStatus: String) = message("parsed.jobStatus", jobStatus)
    fun PARSED_SESSION(sessionId: String) = message("parsed.session", sessionId)
    fun PARSED_COMMAND(command: String) = message("parsed.command", command)
    fun PARSED_NOTE_HEADER() = message("parsed.note")
    fun PARSED_ASYNC_NOTE1() = message("parsed.asyncSubmitted.note1")
    fun PARSED_ASYNC_NOTE2() = message("parsed.asyncSubmitted.note2")
    fun PARSED_FAILED() = message("parsed.failed")
    fun PARSED_REASON(reason: String) = message("parsed.reason", reason)
    fun PARSED_SESSION_EXPIRED_NOTE1() = message("parsed.sessionExpired.note1")
    fun PARSED_SESSION_EXPIRED_NOTE2() = message("parsed.sessionExpired.note2")
    fun PARSED_TIMEOUT() = message("parsed.timeout")
    fun PARSED_TIMEOUT_LIMIT(timeoutMs: Long, seconds: Long) =
        message("parsed.timeout.limit", timeoutMs, seconds)
    fun PARSED_TIMEOUT_DEFAULT_REASON() = message("parsed.timeout.defaultReason")
    fun PARSED_SUGGESTION_HEADER() = message("parsed.suggestion")
    fun PARSED_TIMEOUT_SUGGESTION1() = message("parsed.timeout.suggestion1")
    fun PARSED_TIMEOUT_SUGGESTION2() = message("parsed.timeout.suggestion2")
    fun PARSED_TIMEOUT_SUGGESTION3() = message("parsed.timeout.suggestion3")

    // ── JSON 校验 ──────────────────────────────────────
    fun JSON_HINT_OBJECT() = message("json.hint.object")
    fun JSON_HINT_ARRAY() = message("json.hint.array")
    fun JSON_HINT_MAP() = message("json.hint.map")
    fun JSON_ERROR_OBJECT(paramName: String) = message("json.error.object", paramName)
    fun JSON_ERROR_ARRAY(paramName: String) = message("json.error.array", paramName)
    fun JSON_ERROR_MAP(paramName: String) = message("json.error.map", paramName)
    fun JSON_ERROR_GENERIC(paramName: String) = message("json.error.generic", paramName)

    // ── 参数值校验 ─────────────────────────────────────
    fun VALIDATE_ENUM(paramName: String) = message("validate.enum", paramName)
    fun VALIDATE_BOOLEAN(paramName: String) = message("validate.boolean", paramName)
    fun VALIDATE_CHAR(paramName: String) = message("validate.char", paramName)
    fun VALIDATE_LONG(paramName: String) = message("validate.long", paramName)
    fun VALIDATE_DECIMAL(paramName: String) = message("validate.decimal", paramName)
    fun VALIDATE_BIG_DECIMAL(paramName: String) = message("validate.bigDecimal", paramName)
    fun VALIDATE_BIG_INTEGER(paramName: String) = message("validate.bigInteger", paramName)
    fun VALIDATE_INTEGER(paramName: String) = message("validate.integer", paramName)

    // ── 输入占位提示 ────────────────────────────────────
    fun PARAM_PLACEHOLDER_ENUM() = message("param.placeholder.enum")
    fun PARAM_PLACEHOLDER_LONG() = message("param.placeholder.long")
    fun PARAM_PLACEHOLDER_TIME() = message("param.placeholder.time")
    fun PARAM_PLACEHOLDER_DEFAULT() = message("param.placeholder.default")

    // ── 安全确认正文 ────────────────────────────────────
    fun SECURITY_BODY() = message("security.body")

    // ── 工具窗口工具栏 ──────────────────────────────────
    fun ACTION_CLOSE_ALL_TEXT() = message("action.closeAll.text")
    fun ACTION_CLOSE_ALL_DESCRIPTION() = message("action.closeAll.description")
    fun TAB_COPY_TIP() = message("tab.copyButton.tip")
    fun TAB_TOGGLE_DETAILS_TIP() = message("tab.toggleDetails.tip")

    // ── 设置页补充 ─────────────────────────────────────
    fun SETTINGS_JSON_LIB_AUTO() = message("settings.jsonLib.auto")
}
