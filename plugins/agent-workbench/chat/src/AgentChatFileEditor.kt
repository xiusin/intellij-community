// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

// @spec community/plugins/agent-workbench/spec/agent-chat-editor.spec.md

import com.intellij.CommonBundle
import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeFormatter
import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchContributors
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecs
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.icons.AllIcons
import com.intellij.ide.OccurenceNavigator
import com.intellij.markdown.utils.convertMarkdownToHtml
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.wm.StatusBar
import com.intellij.terminal.frontend.view.TerminalInputInterceptor
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret

internal class AgentChatFileEditor(
  private val project: Project,
  private val file: AgentChatVirtualFile,
  private val terminalTabs: AgentChatTerminalTabs = ToolWindowAgentChatTerminalTabs,
  private val liveTerminalRegistry: AgentChatLiveTerminalRegistry = project.service<AgentChatLiveTerminalRegistryService>(),
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter = ApplicationAgentChatTabSnapshotWriter,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
  private val pendingScopedRefreshRetryIntervalMs: Long = AgentSessionThreadRebindPolicy.PENDING_THREAD_REFRESH_RETRY_INTERVAL_MS,
  editorCoroutineScope: CoroutineScope? = null,
) : UserDataHolderBase(), FileEditor {
  private val ownedTerminalStartupJob = if (editorCoroutineScope == null) SupervisorJob() else null

  @Suppress("RAW_SCOPE_CREATION")
  private val terminalStartupScope = editorCoroutineScope ?: CoroutineScope(checkNotNull(ownedTerminalStartupJob) + Dispatchers.Default)
  private val pendingContextPanel by lazy { AgentChatPendingContextPanel(file.projectPath) }
  private val component = AgentChatFileEditorComponent {
    semanticRegionController?.occurrenceNavigator() ?: OccurenceNavigator.EMPTY
  }

  private val chatBubblePanel = ChatBubblePanel()

  private fun buildEditorTabActions(): ActionGroup? {
    val actionManager = ActionManager.getInstance()
    val providerActionIds = providerDescriptor?.editorTabActionIds.orEmpty()
    val actions = buildList {
      listOf(
        PREVIOUS_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID,
        NEXT_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID,
      ).forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
      providerActionIds.forEach { actionId ->
        actionManager.getAction(actionId)?.let(::add)
      }
    }
    return buildAgentChatEditorTabActionGroup(actions)
  }

  private var tab: AgentChatTerminalTab? = null
  private var initializationStarted: Boolean = false
  private var initializationRequested: Boolean = false
  private var stateApplied: Boolean = file.projectPath.isNotBlank() || file.threadIdentity.isNotBlank()
  private var initializationJob: Job? = null
  private var disposed: Boolean = false
  private var pendingThreadRefreshController: AgentChatPendingThreadRefreshController? = null
  private var codexTerminalTitleThreadRebindController: AgentChatDisposableController? = null
  private var concreteThreadRebindController: AgentChatConcreteThreadRebindController? = null
  private var initialMessageDispatcher: AgentChatInitialMessageDispatcher? = null
  private var scopedTerminalRefreshController: AgentChatDisposableController? = null
  private var terminalRestoreContextController: AgentChatDisposableController? = null
  private var patchFoldController: AgentChatDisposableController? = null
  private var semanticRegionController: AgentChatSemanticRegionController? = null
  private var crossProjectDockTargetRegistration: Disposable? = null

  private val providerDescriptor
    get() = file.provider?.let(AgentSessionProviders::find)

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent {
    return tab?.preferredFocusableComponent ?: component
  }

  override fun getName(): String = AgentChatBundle.message("chat.filetype.name")

  override fun getTabActions(): ActionGroup? = buildEditorTabActions()

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (!file.shouldRestoreOnRestart() || file.projectPath.isBlank() || file.threadIdentity.isBlank()) {
      return AgentChatFileEditorState(snapshot = null)
    }
    return AgentChatFileEditorState(snapshot = file.toSnapshot(), startupIntent = file.startupIntent())
  }

  override fun setState(state: FileEditorState) {
    val chatState = state as? AgentChatFileEditorState ?: return
    stateApplied = true
    val snapshot = chatState.snapshot
    if (snapshot != null) {
      file.updateRestoreOnRestart(true)
      file.updateFromResolution(AgentChatTabResolution.Resolved(snapshot))
      file.updateStartupIntent(chatState.startupIntent)
      ensureCrossProjectDockTargetRegistration()
      FileEditorManager.getInstance(project).updateFilePresentation(file)
    }
    else {
      file.updateRestoreOnRestart(false)
      file.updateStartupIntent(null)
    }
    if (initializationRequested) {
      ensureInitialized()
    }
  }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = !disposed

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

  override fun getFile(): AgentChatVirtualFile = file

  override fun selectNotify() {
    ensureInitialized()
  }

  override fun dispose() {
    disposed = true
    initializationJob?.cancel()
    initializationJob = null
    ownedTerminalStartupJob?.cancel()
    crossProjectDockTargetRegistration?.let(Disposer::dispose)
    crossProjectDockTargetRegistration = null
    initialMessageDispatcher?.dispose()
    initialMessageDispatcher = null
    pendingThreadRefreshController?.dispose()
    pendingThreadRefreshController = null
    codexTerminalTitleThreadRebindController?.dispose()
    codexTerminalTitleThreadRebindController = null
    concreteThreadRebindController?.dispose()
    concreteThreadRebindController = null
    scopedTerminalRefreshController?.dispose()
    scopedTerminalRefreshController = null
    terminalRestoreContextController?.dispose()
    terminalRestoreContextController = null
    patchFoldController?.dispose()
    patchFoldController = null
    semanticRegionController?.dispose()
    semanticRegionController = null
    tab = null
    component.removeAll()
  }

  private fun ensureInitialized() {
    initializationRequested = true
    if (disposed) {
      return
    }
    if (!stateApplied && file.projectPath.isBlank() && file.threadIdentity.isBlank()) {
      return
    }
    val deferredStartState = file.deferredStartState
    if (shouldBlockTerminalInitialization(deferredStartState)) {
      renderDeferredStartState(checkNotNull(deferredStartState))
      return
    }
    if (initializationStarted) {
      return
    }
    val validationError = validateAgentChatFile(file)
    if (validationError != null) {
      handleRestoreValidationError(validationError)
      return
    }
    initializationStarted = true
    val startupLaunchSpecOverride = file.consumeStartupLaunchSpecOverride()
    if (startupLaunchSpecOverride != null) {
      initializeTerminal(
        startupLaunchSpec = startupLaunchSpecOverride,
        suppressInitialMessageDispatch = file.consumeSuppressInitialMessageDispatchOnStartup(),
      )
      return
    }
    val startupIntent = file.startupIntent()
    if (file.isPendingThread && startupIntent == null) {
      handleRestoreValidationError(AgentChatBundle.message("chat.restore.validation.pending.thread"))
      return
    }
    initializationJob = terminalStartupScope.launch {
      try {
        val startupLaunchSpec = resolveStartupLaunchSpec(startupIntent)
        withContext(Dispatchers.EDT) {
          initializeTerminal(startupLaunchSpec)
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        withContext(Dispatchers.EDT) {
          AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, e)
        }
      }
    }
  }

  private suspend fun resolveStartupLaunchSpec(startupIntent: AgentChatStartupIntent?): AgentSessionTerminalLaunchSpec {
    return when (startupIntent) {
      is AgentChatStartupIntent.NewSession -> resolveNewSessionLaunchSpec(startupIntent)
      null -> resolveResumeLaunchSpec()
    }
  }

  private suspend fun resolveNewSessionLaunchSpec(startupIntent: AgentChatStartupIntent.NewSession): AgentSessionTerminalLaunchSpec {
    val descriptor = AgentSessionProviders.find(startupIntent.provider)
                     ?: throw IllegalStateException("Missing Agent Chat provider for ${startupIntent.provider.value}")
    if (startupIntent.launchMode !in descriptor.supportedLaunchModes) {
      throw IllegalStateException("Unsupported Agent Chat launch mode ${startupIntent.launchMode} for ${startupIntent.provider.value}")
    }
    val baseLaunchSpec = descriptor.buildNewSessionLaunchSpec(startupIntent.launchMode)
    val augmented = AgentSessionLaunchSpecs.augment(
      projectPath = file.projectPath,
      provider = startupIntent.provider,
      launchSpec = baseLaunchSpec,
    )
    return AgentSessionLaunchContributors.applyAll(
      projectPath = file.projectPath,
      provider = startupIntent.provider,
      sessionId = null,
      launchSpec = augmented,
    )
  }

  private suspend fun resolveResumeLaunchSpec(): AgentSessionTerminalLaunchSpec {
    val provider = file.provider ?: throw IllegalStateException("Missing Agent Chat provider for ${file.url}")
    return AgentSessionLaunchSpecs.resolveResume(
      projectPath = file.projectPath,
      provider = provider,
      sessionId = file.threadId.ifBlank { file.sessionId },
      launchMode = parseAgentChatLaunchMode(file.launchMode),
    )
  }

  private fun initializeTerminal(
    startupLaunchSpec: AgentSessionTerminalLaunchSpec,
    suppressInitialMessageDispatch: Boolean = false,
  ) {
    if (disposed || tab != null) {
      return
    }
    ensureCrossProjectDockTargetRegistration()
    try {
      val deferredStartState = file.deferredStartState
      if (deferredStartState?.phase == AgentChatDeferredStartPhase.READY_TO_START) {
        file.updateDeferredStartState(null)
      }
      val behavior = resolveAgentChatProviderBehavior(file.provider)
      val createdTab = liveTerminalRegistry.acquireOrCreate(
        file = file,
        terminalTabs = terminalTabs,
        startupLaunchSpec = startupLaunchSpec,
      )
      tab = createdTab
      file.updateStartupIntent(null)
      if (suppressInitialMessageDispatch) {
        // The startup command already carried this prompt; do not snapshot and replay the fallback after title rebind
        // or restore.
        file.clearInitialMessageDispatchMetadata()
      }
      if (file.isPendingThread) {
        file.updateRestoreOnRestart(false)
      }
      val pendingController = AgentChatPendingThreadRefreshController(
        file = file,
        behavior = behavior,
        tabSnapshotWriter = tabSnapshotWriter,
        currentTimeProvider = currentTimeProvider,
        retryIntervalMs = pendingScopedRefreshRetryIntervalMs,
      )
      pendingThreadRefreshController = pendingController
      val concreteController = AgentChatConcreteThreadRebindController(
        file = file,
        behavior = behavior,
        tabSnapshotWriter = tabSnapshotWriter,
        currentTimeProvider = currentTimeProvider,
      )
      concreteThreadRebindController = concreteController
      val messageDispatcher = AgentChatInitialMessageDispatcher(
        file = file,
        behavior = behavior,
        tabSnapshotWriter = tabSnapshotWriter,
      )
      initialMessageDispatcher = messageDispatcher
      pendingController.attach(createdTab)
      concreteController.attach(createdTab, providerDescriptor)
      if (!suppressInitialMessageDispatch) {
        messageDispatcher.schedule(createdTab)
      }
      scopedTerminalRefreshController = createAgentChatScopedTerminalRefreshController(file, createdTab, providerDescriptor)
      val restoreContextController = AgentChatTerminalRestoreContextController(
        file = file,
        descriptor = providerDescriptor,
        parentDisposable = this,
      )
      terminalRestoreContextController = restoreContextController
      restoreContextController.attach(createdTab)
      codexTerminalTitleThreadRebindController = createCodexTerminalTitleThreadRebindController(
        file = file,
        tab = createdTab,
        tabSnapshotWriter = tabSnapshotWriter,
      )
      patchFoldController = behavior.createPatchFoldController(createdTab)
      semanticRegionController = behavior.createSemanticRegionController(createdTab)
      installPendingContextInterceptor(createdTab)
      component.removeAll()
      val contentWrapper = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(chatBubblePanel, BorderLayout.NORTH)
        add(createdTab.component, BorderLayout.CENTER)
      }
      component.add(contentWrapper, BorderLayout.CENTER)
      component.add(pendingContextPanel.component, BorderLayout.SOUTH)
      installAgentChatTerminalFileDropSupport(createdTab.component, createdTab, this)
      installAgentChatContextFileDropSupport(pendingContextPanel.component, ::addPendingContextItems, this)
      component.revalidate()
      component.repaint()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      AgentChatRestoreNotificationService.reportTerminalInitializationFailure(project, file, e)
    }
  }

  private fun ensureCrossProjectDockTargetRegistration() {
    if (crossProjectDockTargetRegistration == null && file.projectPath.isNotBlank()) {
      crossProjectDockTargetRegistration = AgentChatCrossProjectDockTargetRegistrar().register(project, file)
    }
  }

  private fun handleRestoreValidationError(validationError: String) {
    if (file.projectPath.isBlank() && file.threadIdentity.isBlank()) {
      if (!project.isDisposed) {
        FileEditorManager.getInstance(project).closeFile(file)
      }
      return
    }
    forgetAgentChatTabMetadata(file.tabKey)
    AgentChatRestoreNotificationService.reportRestoreFailure(project, file, validationError)
    if (!project.isDisposed) {
      FileEditorManager.getInstance(project).closeFile(file)
    }
  }

  internal fun refreshForFileStateChange() {
    if (disposed) {
      return
    }
    val deferredStartState = file.deferredStartState
    if (tab == null && shouldBlockTerminalInitialization(deferredStartState)) {
      renderDeferredStartState(checkNotNull(deferredStartState))
      return
    }
    ensureInitialized()
  }

  internal fun flushPendingInitialMessageIfInitialized() {
    val initializedTab = tab ?: return
    initialMessageDispatcher?.schedule(initializedTab)
  }

  internal fun addPendingContextItems(items: List<AgentPromptContextItem>): Boolean {
    ensureInitialized()
    val added = pendingContextPanel.addItems(items)
    tab?.preferredFocusableComponent?.requestFocusInWindow()
    return added
  }

  internal fun pendingContextItemsForTests(): List<AgentPromptContextItem> = pendingContextPanel.pendingItemsForTests()

  internal fun canNavigateProposedPlan(direction: AgentChatSemanticNavigationDirection): Boolean {
    return semanticRegionController?.canNavigate(direction) == true
  }

  internal fun navigateProposedPlan(direction: AgentChatSemanticNavigationDirection): Boolean {
    return semanticRegionController?.navigate(direction) == true
  }

  private fun renderDeferredStartState(state: AgentChatDeferredStartState) {
    component.removeAll()
    component.add(createDeferredStartComponent(state), BorderLayout.CENTER)
    component.revalidate()
    component.repaint()
  }

  private fun installPendingContextInterceptor(tab: AgentChatTerminalTab) {
    tab.addInputInterceptor(this, TerminalInputInterceptor { event -> handlePendingContextInput(tab, event) })
  }

  private fun handlePendingContextInput(tab: AgentChatTerminalTab, event: KeyEvent): Boolean {
    if (!pendingContextPanel.hasItems() || !isPlainEnter(event)) {
      return false
    }

    val promptSuffix = resolvePendingContextPromptSuffix() ?: return true
    when (tab.sendPendingContextAndExecute(promptSuffix)) {
      AgentChatPendingContextSubmissionResult.SUBMITTED -> pendingContextPanel.clear()
      AgentChatPendingContextSubmissionResult.UNAVAILABLE -> {
        StatusBar.Info.set(AgentChatBundle.message("chat.pending.context.terminal.unavailable"), project)
      }
    }
    return true
  }

  private fun resolvePendingContextPromptSuffix(): String? {
    val items = pendingContextPanel.pendingItemsSnapshot()
    if (items.isEmpty()) {
      return null
    }

    val softCapChars = AgentPromptContextEnvelopeFormatter.DEFAULT_SOFT_CAP_CHARS
    val serializedChars = pendingContextPanel.measureContextBlockChars(items)
    if (serializedChars <= softCapChars) {
      return pendingContextPanel.buildPromptSuffix(
        items = items,
        summary = AgentPromptContextEnvelopeSummary(
          softCapChars = softCapChars,
          softCapExceeded = false,
          autoTrimApplied = false,
        ),
      )
    }

    val choice = Messages.showDialog(
      project,
      AgentChatBundle.message("chat.pending.context.softcap.message", serializedChars, softCapChars),
      AgentChatBundle.message("chat.pending.context.softcap.title"),
      arrayOf(
        AgentChatBundle.message("chat.pending.context.softcap.action.send.full"),
        AgentChatBundle.message("chat.pending.context.softcap.action.auto.trim"),
        CommonBundle.getCancelButtonText(),
      ),
      0,
      Messages.getWarningIcon(),
    )

    return when (choice) {
      0 -> pendingContextPanel.buildPromptSuffix(
        items = items,
        summary = AgentPromptContextEnvelopeSummary(
          softCapChars = softCapChars,
          softCapExceeded = true,
          autoTrimApplied = false,
        ),
      )
      1 -> {
        val trimResult = AgentPromptContextEnvelopeFormatter.applySoftCap(
          items = items,
          softCapChars = softCapChars,
          projectPath = file.projectPath,
        )
        pendingContextPanel.buildPromptSuffix(
          items = trimResult.items,
          summary = AgentPromptContextEnvelopeSummary(
            softCapChars = softCapChars,
            softCapExceeded = true,
            autoTrimApplied = true,
          ),
        )
      }
      else -> null
    }
  }

  private fun createDeferredStartComponent(state: AgentChatDeferredStartState): JComponent {
    val content = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
    }
    content.add(createMessageArea(state.title, bold = true))
    val stateMessage = state.message
    if (!stateMessage.isNullOrBlank()) {
      content.add(Box.createVerticalStrut(8))
      content.add(createMessageArea(stateMessage, bold = false))
    }
    return JPanel(BorderLayout()).apply {
      add(content, BorderLayout.NORTH)
    }
  }

  private fun createMessageArea(text: @Nls String, bold: Boolean): JComponent {
    return JTextArea(text).apply {
      isEditable = false
      isFocusable = false
      lineWrap = true
      wrapStyleWord = true
      isOpaque = false
      border = null
      font = if (bold) font.deriveFont(font.style or java.awt.Font.BOLD) else font
    }
  }
}

internal enum class ChatMessageRole {
  USER, ASSISTANT
}

internal data class ChatMessage(
  @JvmField val role: ChatMessageRole,
  @JvmField val content: String,
  @JvmField val timestamp: Long = System.currentTimeMillis(),
)

internal class ChatBubblePanel : JPanel(BorderLayout()) {
  private val messageListPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
  }

  private val scrollPane = JBScrollPane(messageListPanel).apply {
    border = null
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    isOpaque = false
    viewport.isOpaque = false
    preferredSize = Dimension(0, 0)
  }

  private val inputArea = JTextArea().apply {
    lineWrap = true
    wrapStyleWord = true
    rows = 3
    border = JBUI.Borders.compound(
      JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
      JBUI.Borders.empty(8, 12, 8, 12),
    )
    font = UIUtil.getLabelFont()
    background = UIUtil.getTextFieldBackground()
    foreground = UIUtil.getTextFieldForeground()
    toolTipText = AgentChatBundle.message("chat.prompt.placeholder")
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER && e.isShiftDown) {
          // Shift+Enter for newline, handled by JTextArea
          return
        }
        if (e.keyCode == KeyEvent.VK_ENTER && e.modifiersEx == 0) {
          e.consume()
          submitMessage()
        }
      }
    })
  }

  private val sendButton = JButton(AllIcons.Actions.Commit).apply {
    isOpaque = false
    isFocusable = false
    contentAreaFilled = false
    border = JBUI.Borders.empty(4)
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addActionListener { submitMessage() }
  }

  private val inputPanel = JPanel(BorderLayout()).apply {
    isOpaque = true
    background = UIUtil.getPanelBackground()
    add(inputArea, BorderLayout.CENTER)
    val sendWrapper = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
      isOpaque = false
      add(sendButton)
    }
    add(sendWrapper, BorderLayout.EAST)
  }

  private var messageSubmitListener: ((String) -> Unit)? = null

  init {
    isOpaque = false
    border = JBUI.Borders.empty()
    add(scrollPane, BorderLayout.CENTER)
    add(inputPanel, BorderLayout.SOUTH)
    isVisible = false
  }

  fun setOnMessageSubmit(listener: (String) -> Unit) {
    messageSubmitListener = listener
  }

  fun addMessage(message: ChatMessage) {
    messageListPanel.add(createBubble(message))
    messageListPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
    isVisible = true
    revalidate()
    repaint()
    SwingUtilities.invokeLater {
      val verticalBar = scrollPane.verticalScrollBar
      verticalBar.value = verticalBar.maximum
    }
  }

  fun clearMessages() {
    messageListPanel.removeAll()
    isVisible = false
    revalidate()
    repaint()
  }

  private fun submitMessage() {
    val text = inputArea.text.trim()
    if (text.isEmpty()) return
    inputArea.text = ""
    messageSubmitListener?.invoke(text)
  }

  private fun createBubble(message: ChatMessage): JPanel {
    val isUser = message.role == ChatMessageRole.USER
    val bubblePanel = JPanel(BorderLayout()).apply {
      isOpaque = false
      border = JBUI.Borders.empty(4, 12, 4, 12)
    }

    val bubbleContent = when {
      message.content.contains("```") -> createCodeBlockBubble(message.content, isUser)
      message.content.contains("**") || message.content.contains("* ") || message.content.startsWith("#") ->
        createMarkdownBubble(message.content, isUser)
      else -> createTextBubble(message.content, isUser)
    }

    val alignment = if (isUser) FlowLayout.RIGHT else FlowLayout.LEFT
    val alignmentPanel = JPanel(FlowLayout(alignment, 0, 0)).apply {
      isOpaque = false
    }

    val maxWidth = (Toolkit.getDefaultToolkit().screenSize.width * 0.6).toInt()
    bubbleContent.maximumSize = Dimension(maxWidth, Int.MAX_VALUE)
    alignmentPanel.add(bubbleContent)

    if (isUser) {
      bubblePanel.add(alignmentPanel, BorderLayout.CENTER)
    }
    else {
      bubblePanel.add(alignmentPanel, BorderLayout.CENTER)
      val actionsPanel = createMessageActions(message)
      bubblePanel.add(actionsPanel, BorderLayout.SOUTH)
    }

    return bubblePanel
  }

  private fun createTextBubble(text: String, isUser: Boolean): JPanel {
    val label = JLabel("<html><body style='width:100%; padding:4px'>${escapeHtml(text)}</body></html>").apply {
      isOpaque = true
      font = UIUtil.getLabelFont()
      border = JBUI.Borders.empty(8, 12, 8, 12)
      if (isUser) {
        background = JBColor(0x3B82F6, 0x2563EB)
        foreground = Color.WHITE
      }
      else {
        background = JBColor(Color(0xF3F4F6), Color(0x374151))
        foreground = UIUtil.getLabelForeground()
      }
    }
    return RoundedPanel(label, isUser)
  }

  private fun createCodeBlockBubble(text: String, isUser: Boolean): JPanel {
    val codeContent = extractCodeFromMarkdown(text)
    val editorPane = JEditorPane().apply {
      editorKit = HTMLEditorKitBuilder().build()
      isEditable = false
      isOpaque = true
      val htmlContent = buildCodeBlockHtml(codeContent, isUser)
      text = htmlContent
      (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
      border = JBUI.Borders.empty(8, 12, 8, 12)
      if (isUser) {
        background = JBColor(0x1E3A5F, 0x1E293B)
        foreground = Color(0xE2E8F0)
      }
      else {
        background = JBColor(Color(0x1E293B), Color(0x0F172A))
        foreground = Color(0xE2E8F0)
      }
    }
    return RoundedPanel(editorPane, isUser)
  }

  private fun createMarkdownBubble(text: String, isUser: Boolean): JPanel {
    val html = convertMarkdownToHtml(text)
    val editorPane = JEditorPane().apply {
      editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
      isEditable = false
      isOpaque = true
      text = "<html><body style='padding:4px;font-family:sans-serif'>$html</body></html>"
      (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
      border = JBUI.Borders.empty(8, 12, 8, 12)
      if (isUser) {
        background = JBColor(0x3B82F6, 0x2563EB)
        foreground = Color.WHITE
      }
      else {
        background = JBColor(Color(0xF3F4F6), Color(0x374151))
        foreground = UIUtil.getLabelForeground()
      }
    }
    return RoundedPanel(editorPane, isUser)
  }

  private fun createMessageActions(message: ChatMessage): JPanel {
    return JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
      isOpaque = false
      border = JBUI.Borders.empty(2, 20, 4, 12)

      add(createIconButton(AllIcons.Actions.Copy, "Copy") {
        val selection = StringSelection(message.content)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
      })

      add(createIconButton(AllIcons.Actions.Refresh, "Retry") {
        // Retry logic - handled by external listener
      })

      add(createIconButton(AllIcons.Actions.Like, "Thumbs Up") {
        // Feedback logic - handled by external listener
      })

      add(createIconButton(AllIcons.Actions.Dislike, "Thumbs Down") {
        // Feedback logic - handled by external listener
      })
    }
  }

  private fun createIconButton(icon: javax.swing.Icon, tooltip: String, action: () -> Unit): JButton {
    return JButton(icon).apply {
      isOpaque = false
      isFocusable = false
      contentAreaFilled = false
      border = JBUI.Borders.empty(2)
      toolTipText = tooltip
      preferredSize = Dimension(24, 24)
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      addActionListener { action() }
    }
  }

  private fun escapeHtml(text: String): String {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
      .replace("\n", "<br>")
  }

  private fun extractCodeFromMarkdown(text: String): String {
    val codeBlockRegex = Regex("```(?:\\w+)?\\s*\\n([\\s\\S]*?)```")
    val match = codeBlockRegex.find(text) ?: return text
    return match.groupValues[1].trim()
  }

  private fun buildCodeBlockHtml(code: String, isUser: Boolean): String {
    val escapedCode = escapeHtml(code)
    val bgColor = if (isUser) "#1E3A5F" else "#1E293B"
    return "<html><body style='font-family:monospace;font-size:13px;padding:8px;background-color:$bgColor;color:#E2E8F0'>" +
           "<pre style='margin:0'>$escapedCode</pre></body></html>"
  }
}

private class RoundedPanel(
  content: JComponent,
  isUser: Boolean,
) : JPanel(BorderLayout()) {
  private val cornerRadius = 12
  private val bgColor = if (isUser) JBColor(0x3B82F6, 0x2563EB) else JBColor(Color(0xF3F4F6), Color(0x374151))

  init {
    isOpaque = false
    border = JBUI.Borders.empty(2)
    add(content, BorderLayout.CENTER)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = bgColor
    g2.fillRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
    g2.dispose()
    super.paintComponent(g)
  }
}

private fun isPlainEnter(event: KeyEvent): Boolean {
  return event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ENTER && event.modifiersEx == 0
}

private fun shouldBlockTerminalInitialization(state: AgentChatDeferredStartState?): Boolean {
  return when (state?.phase) {
    AgentChatDeferredStartPhase.WAITING,
    AgentChatDeferredStartPhase.SUCCESS_NO_START,
    AgentChatDeferredStartPhase.FAILURE_NO_START,
      -> true

    else -> false
  }
}

private class AgentChatFileEditorComponent(
  private val navigatorProvider: () -> OccurenceNavigator,
) : JPanel(BorderLayout()), OccurenceNavigator {
  override fun hasNextOccurence(): Boolean = navigatorProvider().hasNextOccurence()

  override fun hasPreviousOccurence(): Boolean = navigatorProvider().hasPreviousOccurence()

  override fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? = navigatorProvider().goNextOccurence()

  override fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? = navigatorProvider().goPreviousOccurence()

  override fun getNextOccurenceActionName(): String = navigatorProvider().nextOccurenceActionName

  override fun getPreviousOccurenceActionName(): String = navigatorProvider().previousOccurenceActionName

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal fun interface AgentChatTabSnapshotWriter {
  suspend fun upsert(snapshot: AgentChatTabSnapshot)
}

private object ApplicationAgentChatTabSnapshotWriter : AgentChatTabSnapshotWriter {
  @Suppress("UNUSED_PARAMETER")
  override suspend fun upsert(snapshot: AgentChatTabSnapshot) = Unit
}

internal fun buildAgentChatEditorTabActionGroup(actions: List<AnAction>): ActionGroup? {
  if (actions.isEmpty()) {
    return null
  }
  if (actions.size == 1) {
    val singleAction = actions.single()
    return singleAction as? ActionGroup ?: DumbAwareAgentChatActionGroup(singleAction)
  }
  return DumbAwareAgentChatActionGroup(actions)
}

private class DumbAwareAgentChatActionGroup : DefaultActionGroup, DumbAware {
  constructor(vararg actions: AnAction) : super(*actions)

  constructor(actions: List<AnAction>) : super(actions)
}

private const val PREVIOUS_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID: String =
  AgentWorkbenchActionIds.Sessions.EditorTab.PREVIOUS_PROPOSED_PLAN
private const val NEXT_PROPOSED_PLAN_FROM_EDITOR_TAB_ACTION_ID: String = AgentWorkbenchActionIds.Sessions.EditorTab.NEXT_PROPOSED_PLAN
