package com.shopmanager.app.ui.chat

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.chat.ChatAccount
import com.shopmanager.app.data.chat.ChatAuthRepository
import com.shopmanager.app.data.chat.ChatFirebase
import com.shopmanager.app.data.chat.ChatMessage
import com.shopmanager.app.data.chat.ChatMeta
import com.shopmanager.app.data.chat.ChatRepository
import com.shopmanager.app.data.chat.ChatSummary
import com.shopmanager.app.data.chat.ChatUser
import com.shopmanager.app.data.chat.MAX_VOICE_MS
import com.shopmanager.app.data.chat.VoicePlayer
import com.shopmanager.app.data.chat.VoiceRecorder
import com.shopmanager.app.data.chat.chatErrorMessage
import com.shopmanager.app.data.chat.chatIdFor
import com.shopmanager.app.data.retryOnError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
// الجلسة: حالة تسجيل الدخول + قائمة المحادثات (نسخة واحدة مشتركة، تُنشأ من ShopManagerApp).
// لا تبدأ أي عملية Firebase للدردشة قبل أن تُعرض شاشة الدردشة فعلاً (WhileSubscribed).
// ════════════════════════════════════════════════════════════════════════════

sealed interface ChatSessionState {
    data object Loading : ChatSessionState
    data object SignedOut : ChatSessionState
    data class SignedIn(val account: ChatAccount) : ChatSessionState
}

data class AuthUiState(val busy: Boolean = false, val error: String? = null, val info: String? = null)

data class ChatListState(val chats: List<ChatSummary> = emptyList(), val loading: Boolean = true)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = ChatAuthRepository(application)
    private val repo = ChatRepository(application)

    val session: StateFlow<ChatSessionState> = authRepo.accountFlow()
        .map<ChatAccount?, ChatSessionState> { if (it == null) ChatSessionState.SignedOut else ChatSessionState.SignedIn(it) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatSessionState.Loading)

    private val _chatsError = MutableStateFlow<String?>(null)
    val chatsError: StateFlow<String?> = _chatsError

    val chatList: StateFlow<ChatListState> = session
        .flatMapLatest { s ->
            if (s is ChatSessionState.SignedIn) {
                repo.listenMyChats(s.account.uid)
                    .retryOnError { _chatsError.value = chatErrorMessage(it) }
                    .onEach { _chatsError.value = null }
                    .map { ChatListState(it, false) }
            } else flowOf(ChatListState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatListState())

    private val _auth = MutableStateFlow(AuthUiState())
    val auth: StateFlow<AuthUiState> = _auth

    fun clearAuthMessages() { _auth.update { it.copy(error = null, info = null) } }

    fun signUp(name: String, email: String, password: String) = runAuth { authRepo.signUp(name, email, password) }

    fun signIn(email: String, password: String) = runAuth { authRepo.signIn(email, password) }

    fun sendReset(email: String) {
        viewModelScope.launch {
            _auth.value = AuthUiState(busy = true)
            try {
                authRepo.sendReset(email)
                _auth.value = AuthUiState(info = "أرسلنا رابط إعادة تعيين كلمة المرور إلى بريدك")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _auth.value = AuthUiState(error = chatErrorMessage(e))
            }
        }
    }

    private fun runAuth(block: suspend () -> Unit) {
        if (_auth.value.busy) return
        viewModelScope.launch {
            _auth.value = AuthUiState(busy = true)
            try {
                block()
                _auth.value = AuthUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _auth.value = AuthUiState(error = chatErrorMessage(e))
            }
        }
    }

    fun signOut() {
        VoicePlayer.stop()
        authRepo.signOut()
        _auth.value = AuthUiState()
    }

    /** نبضة "متصل الآن" — تُستدعى من الواجهة أثناء ظهورها فقط. */
    suspend fun heartbeat() {
        val uid = authRepo.currentUid() ?: return
        repo.heartbeat(uid)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// اختيار مستخدم لبدء محادثة
// ════════════════════════════════════════════════════════════════════════════

data class PickerState(val users: List<ChatUser> = emptyList(), val loading: Boolean = true, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class UserPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    private val me: String = ChatAuthRepository(application).currentUid().orEmpty()

    val query = MutableStateFlow("")
    private val retry = MutableStateFlow(0)

    val state: StateFlow<PickerState> = combine(query.debounce { if (it.isEmpty()) 0L else 300L }.distinctUntilChanged(), retry) { q, _ -> q }
        .flatMapLatest { q ->
            flow {
                emit(PickerState(loading = true))
                try {
                    emit(PickerState(users = repo.searchUsers(q, me), loading = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(PickerState(loading = false, error = chatErrorMessage(e)))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PickerState())

    fun reload() { retry.update { it + 1 } }
}

// ════════════════════════════════════════════════════════════════════════════
// شاشة المحادثة
// ════════════════════════════════════════════════════════════════════════════

data class MessagesState(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = true,
    val hasMore: Boolean = false
)

data class RecordingUi(
    val active: Boolean = false,
    val elapsedMs: Long = 0L,
    val level: Int = 0,
    val willCancel: Boolean = false
)

private const val PAGE_SIZE = 50L

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    application: Application,
    val otherUid: String,
    private val nameHint: String
) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    val me: String = ChatAuthRepository(application).currentUid().orEmpty()
    val chatId: String = chatIdFor(me, otherUid)

    private val limit = MutableStateFlow(PAGE_SIZE)
    private val active = MutableStateFlow(false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /** رسالة قصيرة للمستخدم (Snackbar) من الواجهة نفسها: صلاحية، نسخ... */
    fun showHint(text: String) { _message.value = text }

    val messages: StateFlow<MessagesState> = limit
        .flatMapLatest { l ->
            repo.listenMessages(chatId, l)
                .retryOnError { _message.value = "تعذّر تحديث الرسائل، سنعيد المحاولة" }
                .map { MessagesState(it, false, it.size >= l) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MessagesState())

    /** null = لم تصل أول لقطة بعد. */
    private val meta: StateFlow<ChatMeta?> = repo.listenMeta(chatId, me, otherUid)
        .map<ChatMeta, ChatMeta?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val otherReadMs: StateFlow<Long> = meta.map { it?.otherReadMs ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val other: StateFlow<ChatUser?> = repo.listenUser(otherUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val otherName: String get() = other.value?.name ?: nameHint.ifBlank { "مستخدم" }

    private val _otherTyping = MutableStateFlow(false)
    val otherTyping: StateFlow<Boolean> = _otherTyping

    private val _recording = MutableStateFlow(RecordingUi())
    val recording: StateFlow<RecordingUi> = _recording

    private val recorder = VoiceRecorder(application)
    private var recJob: Job? = null
    private var myNameCache: String? = null
    private var typingOn = false
    private var lastTypingSent = 0L
    private var lastMarkedId: String? = null

    // آخر مرة رأينا فيها تغيّراً بمؤشر كتابة الطرف الآخر.
    private var typingTouched = 0L

    init {
        // مؤشر "يكتب الآن": لا نقارن الوقت بساعة الجهاز (قد تختلف عن الخادم)؛ نعتبره يكتب ما دامت
        // قيمة المؤشر تتغير خلال آخر 6 ثوانٍ كما نراها نحن. أول لقطة تُعتبر خط الأساس فقط.
        viewModelScope.launch {
            var baseline = false
            var stamp = 0L
            meta.collect { m ->
                if (m == null) return@collect
                if (!baseline) {
                    baseline = true
                    stamp = m.otherTypingStamp
                    return@collect
                }
                if (m.otherTypingStamp != stamp) {
                    stamp = m.otherTypingStamp
                    typingTouched = SystemClock.elapsedRealtime()
                    _otherTyping.value = stamp != 0L
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_otherTyping.value && SystemClock.elapsedRealtime() - typingTouched > 6000) {
                    _otherTyping.value = false
                }
            }
        }
        // تنظيف الرسائل الصوتية المنتهية (أقدم من 30 يوماً) عند فتح المحادثة؛ بدل TTL.
        viewModelScope.launch {
            val tried = HashSet<String>()
            messages.collect { st ->
                val expired = st.messages.filter { it.isExpiredVoice && !it.pending && tried.add(it.id) }
                if (expired.isNotEmpty()) repo.deleteExpiredVoice(chatId, expired)
            }
        }
        // وضع علامة "مقروء" عندما تصل رسالة واردة والشاشة ظاهرة.
        viewModelScope.launch {
            combine(messages, meta, active) { m, mt, a -> Triple(m.messages.lastOrNull { it.fromUid != me }, mt, a) }
                .collect { (lastIn, mt, a) ->
                    if (a && mt != null && lastIn != null && !lastIn.pending &&
                        lastIn.createdAtMs > mt.myReadMs && lastIn.id != lastMarkedId
                    ) {
                        lastMarkedId = lastIn.id
                        repo.markRead(chatId, me)
                    }
                }
        }
    }

    fun setActive(a: Boolean) { active.value = a }

    /** نبضة "متصل الآن" أثناء فتح المحادثة. */
    suspend fun heartbeat() { repo.heartbeat(me) }

    fun loadOlder() { limit.update { it + PAGE_SIZE } }

    private suspend fun myName(): String {
        myNameCache?.let { return it }
        val n = repo.getUser(me)?.name
            ?: ChatFirebase.auth(getApplication()).currentUser?.displayName?.takeIf { it.isNotBlank() }
        if (n != null) myNameCache = n
        return n ?: "مستخدم"
    }

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        typingOn = false
        viewModelScope.launch {
            try {
                repo.sendText(me, myName(), otherUid, otherName, t)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = chatErrorMessage(e).let { if (it.startsWith("حدث خطأ")) "تعذّر إرسال الرسالة" else it }
            }
        }
    }

    fun onTextChanged(t: String) {
        val now = SystemClock.elapsedRealtime()
        if (t.isBlank()) {
            if (typingOn) {
                typingOn = false
                viewModelScope.launch { repo.setTyping(chatId, me, false) }
            }
        } else if (!typingOn || now - lastTypingSent > 3000) {
            typingOn = true
            lastTypingSent = now
            viewModelScope.launch { repo.setTyping(chatId, me, true) }
        }
    }

    // ───────────── الرسائل الصوتية ─────────────

    fun startRecording(): Boolean {
        if (recorder.isRecording) return false
        VoicePlayer.stop()
        if (!recorder.start()) {
            _message.value = "تعذّر بدء التسجيل، تأكد من صلاحية المايك"
            return false
        }
        _recording.value = RecordingUi(active = true)
        recJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                val level = recorder.sampleLevel()
                val elapsed = recorder.elapsedMs()
                _recording.update { it.copy(elapsedMs = elapsed, level = level) }
                if (elapsed >= MAX_VOICE_MS) {
                    finishRecording(send = true)
                    break
                }
            }
        }
        return true
    }

    fun setWillCancel(v: Boolean) { _recording.update { if (it.willCancel == v) it else it.copy(willCancel = v) } }

    fun finishRecording(send: Boolean) {
        if (!_recording.value.active) return
        recJob?.cancel()
        recJob = null
        _recording.value = RecordingUi()
        if (!send) {
            recorder.cancel()
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val voice = recorder.stop()
            if (voice == null) {
                _message.value = "التسجيل قصير جداً أو كبير الحجم، اضغط مطولاً وتحدّث ثم أفلت"
                return@launch
            }
            try {
                repo.sendVoice(me, myName(), otherUid, otherName, voice.audioB64, voice.durationMs, voice.wave)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "تعذّر إرسال الرسالة الصوتية"
            }
        }
    }

    override fun onCleared() {
        recorder.cancel()
        VoicePlayer.stop()
        if (typingOn) {
            val r = repo
            val c = chatId
            val m = me
            CoroutineScope(Dispatchers.IO).launch { r.setTyping(c, m, false) }
        }
        super.onCleared()
    }

    class Factory(
        private val app: Application,
        private val otherUid: String,
        private val nameHint: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConversationViewModel(app, otherUid, nameHint) as T
    }
}
