package com.runanywhere.lifecycle

import com.intellij.ide.AppLifecycleListener
import com.runanywhere.concurrent.RunAnywhereExecutors

/**
 * 应用生命周期监听：IDE 关闭时释放插件线程池。
 */
class RunAnywhereAppListener : AppLifecycleListener {

    override fun appWillBeClosed(isRestart: Boolean) {
        RunAnywhereExecutors.shutdown()
    }
}
