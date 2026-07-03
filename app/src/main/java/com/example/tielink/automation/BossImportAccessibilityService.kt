package com.example.tielink.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.tielink.data.local.db.entity.JdLibraryEntity
import com.example.tielink.data.repository.JdLibraryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BossImportAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "BossImport"
    }

    @Inject
    lateinit var jdLibraryRepository: JdLibraryRepository

    private enum class Phase { OPEN_SEARCH, RESULTS, DETAIL }

    private data class Candidate(
        val fingerprint: String,
        val position: String,
        val company: String,
        val salary: String
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var phase = Phase.OPEN_SEARCH
    private var imported = 0
    private var processing = false
    private var saveJob: Job? = null
    private var currentCandidate: Candidate? = null
    private val visited = mutableSetOf<String>()
    private val salaryRegex = Regex(
        """(?i)(?:\d{1,6}\s*[-–~]\s*\d{1,6}\s*(?:[kK]|元/(?:天|时|月))|面议)"""
    )
    private val pollRunnable = object : Runnable {
        override fun run() {
            val command = BossImportController.readCommand(this@BossImportAccessibilityService)
            val root = rootInActiveWindow
            if (
                command != null &&
                root?.packageName?.toString() == BossImportController.BOSS_PACKAGE &&
                !processing
            ) {
                processing = true
                try {
                    process(command.first, command.second)
                } catch (error: Exception) {
                    Log.e(TAG, "Polling failed", error)
                } finally {
                    processing = false
                }
            }
            handler.postDelayed(this, 1_200)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        BossImportController.readCommand(this)?.let {
            Log.d(TAG, "Service connected, pending keyword=${it.first}")
            BossImportController.update(imported, "自动化服务已连接")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != BossImportController.BOSS_PACKAGE) return
        val command = BossImportController.readCommand(this) ?: return
        if (processing) return
        processing = true
        handler.postDelayed({
            try {
                process(command.first, command.second)
            } finally {
                processing = false
            }
        }, 700)
    }

    override fun onInterrupt() {
        BossImportController.stop(this, "无障碍服务已中断")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun process(keyword: String, limit: Int) {
        val root = rootInActiveWindow ?: return
        when (phase) {
            Phase.OPEN_SEARCH -> openSearch(root, keyword, limit)
            Phase.RESULTS -> openNextResult(root, limit)
            Phase.DETAIL -> importDetail(root, limit)
        }
    }

    private fun openSearch(root: AccessibilityNodeInfo, keyword: String, limit: Int) {
        val positionNode = findByIdSuffix(root, "tv_position_name")
        val salaryNode = findFirst(root) { salaryRegex.containsMatchIn(nodeLabel(it)) }
        if (positionNode != null && salaryNode != null) {
            phase = Phase.RESULTS
            Log.d(TAG, "Search results detected directly")
            openNextResult(root, limit)
            return
        }

        val input = findFirst(root) {
            it.isEditable || it.className?.toString()?.contains("EditText") == true
        }
        if (input == null) {
            val homeSearch = findByIdSuffix(root, "ly_menu")
            val homeSearchButton = homeSearch?.let { findFirst(it) { node -> node.isClickable } }
            val searchEntry = homeSearchButton ?: findFirst(root) {
                val label = nodeLabel(it)
                it.isClickable && (label.contains("搜索职位") || label == "搜索")
            }
            if (searchEntry?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                Log.d(TAG, "Opened search from home")
                BossImportController.update(imported, "正在进入岗位搜索")
                return
            }

            val resultSearchBar = findByIdSuffix(root, "constraintLayout_search_input")
            if (resultSearchBar?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                Log.d(TAG, "Opened search input from result page")
                BossImportController.update(imported, "正在填写岗位关键词")
            }
            return
        }

        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        input.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
            }
        )

        val searchButton = findFirst(root) {
            it.isClickable && nodeLabel(it).let { label ->
                label == "搜索" || label == "搜职位"
            }
        }
        val submitted = searchButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (submitted) {
            phase = Phase.RESULTS
            Log.d(TAG, "Submitted search for $keyword")
            BossImportController.update(imported, "正在读取“$keyword”的搜索结果")
        }
    }

    private fun openNextResult(root: AccessibilityNodeInfo, limit: Int) {
        if (imported >= limit) {
            BossImportController.stop(this, "已完成，共导入 $imported 个岗位")
            return
        }

        val salaryNodes = mutableListOf<AccessibilityNodeInfo>()
        walk(root) { node ->
            if (salaryRegex.containsMatchIn(nodeLabel(node))) salaryNodes += node
        }

        for (salaryNode in salaryNodes) {
            val card = clickableAncestor(salaryNode) ?: continue
            val lines = collectLabels(card)
            val fingerprint = lines.joinToString("|").take(300)
            if (fingerprint.isBlank() || !visited.add(fingerprint)) continue

            currentCandidate = Candidate(
                fingerprint = fingerprint,
                position = findByIdSuffix(card, "tv_position_name")?.let(::nodeLabel)
                    ?: parseCandidate(lines, fingerprint).position,
                company = findByIdSuffix(card, "tv_company_name")?.let(::nodeLabel)
                    ?: parseCandidate(lines, fingerprint).company,
                salary = findByIdSuffix(card, "tv_salary_statue")?.let(::nodeLabel)
                    ?: parseCandidate(lines, fingerprint).salary
            )
            if (card.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                phase = Phase.DETAIL
                Log.d(TAG, "Opened job detail: ${currentCandidate?.position}")
                BossImportController.update(imported, "正在读取第 ${imported + 1}/$limit 个岗位详情")
                return
            }
        }

        val scrollable = findFirst(root) { it.isScrollable }
        if (scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true) {
            BossImportController.update(imported, "正在加载更多岗位")
        } else {
            BossImportController.stop(this, "搜索结果读取完毕，共导入 $imported 个岗位")
        }
    }

    private fun importDetail(root: AccessibilityNodeInfo, limit: Int) {
        val lines = collectLabels(root)
        val rawText = lines.distinct().joinToString("\n")
        val isDetail = lines.any {
            it.contains("职位描述") || it.contains("岗位职责") ||
                it.contains("任职要求") || it.contains("职位详情")
        }
        if (!isDetail || rawText.length < 80) return

        val candidate = currentCandidate ?: parseCandidate(lines, rawText.take(300))
        phase = Phase.RESULTS
        saveJob = serviceScope.launch {
            jdLibraryRepository.insert(
                JdLibraryEntity(
                    companyName = candidate.company,
                    positionName = candidate.position,
                    salary = candidate.salary,
                    rawText = rawText,
                    sourceType = "boss_auto"
                )
            )
            imported += 1
            Log.d(TAG, "Saved job ${candidate.position}, imported=$imported")
            BossImportController.update(imported, "已导入 $imported/$limit，正在读取下一个")
            handler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
                if (imported >= limit) {
                    BossImportController.stop(
                        this@BossImportAccessibilityService,
                        "已完成，共导入 $imported 个岗位"
                    )
                }
            }, 350)
        }
    }

    private fun parseCandidate(lines: List<String>, fingerprint: String): Candidate {
        val cleaned = lines.map(String::trim).filter(String::isNotBlank).distinct()
        val salary = cleaned.firstOrNull { salaryRegex.containsMatchIn(it) }.orEmpty()
        val position = cleaned.firstOrNull {
            it.length in 2..40 &&
                !salaryRegex.containsMatchIn(it) &&
                !it.contains("经验") &&
                !it.contains("学历") &&
                !it.contains("招聘")
        }.orEmpty()
        val company = cleaned.firstOrNull {
            it != position && (
                it.contains("公司") || it.contains("科技") || it.contains("集团") ||
                    it.contains("有限") || it.contains("工作室")
                )
        }.orEmpty()
        return Candidate(fingerprint, position, company, salary)
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    private fun collectLabels(root: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        walk(root) { node ->
            nodeLabel(node).trim().takeIf(String::isNotBlank)?.let(result::add)
        }
        return result
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        return node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: ""
    }

    private fun findFirst(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            findFirst(child, predicate)?.let { return it }
        }
        return null
    }

    private fun findByIdSuffix(
        root: AccessibilityNodeInfo,
        suffix: String
    ): AccessibilityNodeInfo? {
        return findFirst(root) {
            it.viewIdResourceName?.substringAfterLast('/') == suffix
        }
    }

    private fun walk(
        root: AccessibilityNodeInfo,
        action: (AccessibilityNodeInfo) -> Unit
    ) {
        action(root)
        for (index in 0 until root.childCount) {
            root.getChild(index)?.let { walk(it, action) }
        }
    }
}
