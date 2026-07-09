package com.example.tielink.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
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
        private val SEARCH_LABEL_HINTS = listOf(
            "搜索",
            "搜职位",
            "搜索职位",
            "搜索框",
            "请输入",
            "职位",
            "岗位",
            "公司"
        )
        private val SEARCH_ID_HINTS = listOf(
            "search",
            "query",
            "keyword",
            "edit",
            "input",
            "bar"
        )
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
    private var activeSessionId = 0L
    private var activeKeyword = ""
    private var activeLimit = 0
    private var openSearchAttempts = 0
    private var lastSubmitAttemptAt = 0L
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
                    process(command)
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
        BossImportController.readCommand(this)?.let { command ->
            resetSession(command)
            Log.d(TAG, "Service connected, pending keyword=${command.keyword}")
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
                process(command)
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

    private fun process(command: BossImportCommand) {
        ensureSession(command)
        val root = rootInActiveWindow ?: return
        when (phase) {
            Phase.OPEN_SEARCH -> openSearch(root, command.keyword, command.limit)
            Phase.RESULTS -> openNextResult(root, command.limit)
            Phase.DETAIL -> importDetail(root, command.limit)
        }
    }

    private fun openSearch(root: AccessibilityNodeInfo, keyword: String, limit: Int) {
        openSearchAttempts += 1
        if (isKeywordResultsPage(root, keyword)) {
            openSearchAttempts = 0
            phase = Phase.RESULTS
            Log.d(TAG, "Detected search results for keyword=$keyword")
            BossImportController.update(imported, "已定位到“$keyword”的搜索结果")
            openNextResult(root, limit)
            return
        }

        val input = findFirst(root) {
            it.isEditable || it.className?.toString()?.contains("EditText") == true
        }
        if (input == null) {
            val searchCandidates = findSearchEntryCandidates(root)
            val indexedCandidate = searchCandidates.getOrNull((openSearchAttempts - 1) % searchCandidates.size.coerceAtLeast(1))
            if (indexedCandidate != null && clickNode(indexedCandidate)) {
                Log.d(
                    TAG,
                    "Opened search entry attempt=$openSearchAttempts node=${describeNode(indexedCandidate)}"
                )
                BossImportController.update(imported, "正在打开搜索框")
                return
            }
            if (tapTopSearchArea(root)) {
                Log.d(TAG, "Tapped top search area as fallback")
                BossImportController.update(imported, "正在尝试打开顶部搜索框")
                if (openSearchAttempts % 3 == 0) {
                    dumpSearchNodes(root, "openSearch:no_input:tap_fallback")
                }
                return
            }
            dumpSearchNodes(root, "openSearch:no_input")
            BossImportController.update(imported, "没找到搜索框，请手动点一下顶部搜索框")
            return
        }

        openSearchAttempts = 0
        val currentInputText = normalizeText(nodeLabel(input))
        val desiredKeyword = normalizeText(keyword)
        val filled = currentInputText == desiredKeyword || setText(input, keyword)
        if (!filled) {
            dumpSearchNodes(root, "openSearch:set_text_failed")
            BossImportController.update(imported, "搜索框已出现，但自动填词失败，请手动点一下搜索框")
            return
        }

        if (isKeywordResultsPage(root, keyword)) {
            phase = Phase.RESULTS
            Log.d(TAG, "Submitted search for $keyword")
            BossImportController.update(imported, "正在读取“$keyword”的搜索结果")
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastSubmitAttemptAt < 1_500) {
            BossImportController.update(imported, "已填写关键词，等待搜索结果")
            return
        }

        lastSubmitAttemptAt = now
        val submitted = submitSearch(root, input)
        if (submitted) {
            BossImportController.update(imported, "已填写关键词，正在触发搜索")
        } else {
            BossImportController.update(imported, "已填写关键词，但没找到提交按钮，请手动点搜索")
            dumpSearchNodes(root, "openSearch:submit_failed")
            Log.w(TAG, "Search submit node not found for keyword=$keyword")
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

    private fun ensureSession(command: BossImportCommand) {
        if (
            command.sessionId != activeSessionId ||
            command.keyword != activeKeyword ||
            command.limit != activeLimit
        ) {
            resetSession(command)
        }
    }

    private fun resetSession(command: BossImportCommand) {
        activeSessionId = command.sessionId
        activeKeyword = command.keyword
        activeLimit = command.limit
        phase = Phase.OPEN_SEARCH
        imported = 0
        openSearchAttempts = 0
        lastSubmitAttemptAt = 0L
        currentCandidate = null
        visited.clear()
        saveJob?.cancel()
        saveJob = null
    }

    private fun setText(
        input: AccessibilityNodeInfo,
        keyword: String
    ): Boolean {
        input.refresh()
        input.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val success = input.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword)
            }
        )
        if (success) return true
        val ancestor = clickableAncestor(input)
        if (ancestor != null) {
            ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return input.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        keyword
                    )
                }
            )
        }
        return false
    }

    private fun submitSearch(
        root: AccessibilityNodeInfo,
        input: AccessibilityNodeInfo
    ): Boolean {
        val searchButton = findSearchActionNode(root, input)
        var issued = false
        if (submitSearchFromInput(input)) {
            issued = true
        }
        if (clickNode(searchButton)) {
            Log.d(TAG, "Submitted search by visible search button")
            issued = true
        }
        if (tapSearchSubmitArea(root, input)) {
            Log.d(TAG, "Submitted search by right-side tap fallback")
            issued = true
        }
        return issued
    }

    private fun submitSearchFromInput(input: AccessibilityNodeInfo): Boolean {
        input.refresh()
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        var issued = false
        val imeEnterActionId = AccessibilityAction.ACTION_IME_ENTER.id
        if (input.actionList.any { it.id == imeEnterActionId || normalizeText(it.label?.toString().orEmpty()).contains("搜索") }) {
            if (input.performAction(imeEnterActionId)) {
                Log.d(TAG, "Submitted search by ACTION_IME_ENTER")
                issued = true
            }
        }
        val fallbackAction = input.actionList.firstOrNull { action ->
            val label = normalizeText(action.label?.toString().orEmpty())
            label.contains("搜索") || label.contains("search") || label.contains("enter")
        }
        if (fallbackAction != null && input.performAction(fallbackAction.id)) {
            Log.d(TAG, "Submitted search by input action label=${fallbackAction.label}")
            issued = true
        }
        if (tapKeyboardSearchKey(input)) {
            issued = true
        }
        return issued
    }

    private fun findSearchActionNode(
        root: AccessibilityNodeInfo,
        input: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        val inputBounds = Rect().also { input.getBoundsInScreen(it) }
        val idSuffixHints = setOf(
            "tv_search",
            "btn_search",
            "search_btn",
            "iv_search",
            "search",
            "right_text"
        )
        return findFirst(root) { node ->
            val label = normalizeText(nodeLabel(node))
            val idSuffix = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val nearInput =
                inputBounds.width() > 0 &&
                    bounds.centerY() in (inputBounds.top - 80)..(inputBounds.bottom + 80) &&
                    bounds.centerX() >= inputBounds.centerX()
            val textMatch = label.contains("搜索") || label.contains("搜职位")
            val idMatch = idSuffixHints.any { hint -> idSuffix.contains(hint, ignoreCase = true) }
            (textMatch || idMatch) && nearInput
        } ?: findFirst(root) { node ->
            val label = normalizeText(nodeLabel(node))
            label.contains("搜索") || label.contains("搜职位")
        }
    }

    private fun findSearchEntryCandidates(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val topThreshold = rootBounds.top + (rootBounds.height() * 0.35f).toInt()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        walk(root) { node ->
            val label = normalizeText(nodeLabel(node))
            val idSuffix = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val nearTop = bounds.top in rootBounds.top..topThreshold
            val textMatch = SEARCH_LABEL_HINTS.any { hint -> label.contains(normalizeText(hint)) }
            val idMatch = SEARCH_ID_HINTS.any { hint -> idSuffix.contains(hint, ignoreCase = true) }
            if (nearTop && (textMatch || idMatch)) {
                candidates += node
            }
        }
        return candidates.distinctBy { describeNode(it) }.sortedBy { node ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            bounds.top * 10_000 + bounds.left
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        clickableAncestor(node)?.let { ancestor ->
            if (ancestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return tapNodeCenter(node)
    }

    private fun tapNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        return tapAt(bounds.exactCenterX(), bounds.exactCenterY())
    }

    private fun tapTopSearchArea(root: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        val x = bounds.exactCenterX()
        val y = bounds.top + bounds.height() * 0.14f
        return tapAt(x, y)
    }

    private fun tapSearchSubmitArea(
        root: AccessibilityNodeInfo,
        input: AccessibilityNodeInfo
    ): Boolean {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val inputBounds = Rect().also { input.getBoundsInScreen(it) }
        if (rootBounds.isEmpty || inputBounds.isEmpty) return false
        val x = rootBounds.right - (rootBounds.width() * 0.12f)
        val y = inputBounds.exactCenterY()
        return tapAt(x, y)
    }

    private fun tapKeyboardSearchKey(input: AccessibilityNodeInfo): Boolean {
        val inputBounds = Rect().also { input.getBoundsInScreen(it) }
        val rootBounds = Rect().also { rootInActiveWindow?.getBoundsInScreen(it) }
        if (inputBounds.isEmpty || rootBounds.isEmpty) return false
        val x = rootBounds.right - (rootBounds.width() * 0.08f)
        val y = rootBounds.bottom - (rootBounds.height() * 0.06f)
        val tapped = tapAt(x, y)
        if (tapped) {
            Log.d(TAG, "Submitted search by keyboard key fallback x=$x y=$y")
        }
        return tapped
    }

    private fun tapAt(
        x: Float,
        y: Float
    ): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dumpSearchNodes(
        root: AccessibilityNodeInfo,
        reason: String
    ) {
        val rootBounds = Rect().also { root.getBoundsInScreen(it) }
        val topThreshold = rootBounds.top + (rootBounds.height() * 0.45f).toInt()
        val logs = mutableListOf<String>()
        walk(root) { node ->
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val label = nodeLabel(node).trim()
            val idSuffix = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val interesting =
                bounds.top <= topThreshold &&
                    (
                        label.isNotBlank() ||
                            SEARCH_ID_HINTS.any { hint -> idSuffix.contains(hint, ignoreCase = true) }
                        )
            if (interesting && logs.size < 30) {
                logs += "id=$idSuffix class=${node.className} text=$label click=${node.isClickable} " +
                    "editable=${node.isEditable} bounds=$bounds"
            }
        }
        Log.w(TAG, "Search diagnostics [$reason]\n${logs.joinToString("\n")}")
    }

    private fun describeNode(node: AccessibilityNodeInfo): String {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val idSuffix = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
        return "id=$idSuffix class=${node.className} text=${nodeLabel(node).trim()} bounds=$bounds click=${node.isClickable}"
    }

    private fun isKeywordResultsPage(
        root: AccessibilityNodeInfo,
        keyword: String
    ): Boolean {
        val positionNode = findByIdSuffix(root, "tv_position_name")
        val salaryNode = findFirst(root) { salaryRegex.containsMatchIn(nodeLabel(it)) }
        if (positionNode == null || salaryNode == null) return false

        val normalizedKeyword = normalizeText(keyword)
        return collectLabels(root)
            .take(80)
            .map(::normalizeText)
            .any { label -> normalizedKeyword.isNotBlank() && label.contains(normalizedKeyword) }
    }

    private fun normalizeText(value: String): String {
        return value.lowercase().replace("\\s+".toRegex(), "")
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
