package com.example.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SendMoneyReceipt
import com.example.data.model.User
import com.example.data.model.WalletActivityCategory
import com.example.data.model.WalletActivityItem
import com.example.data.model.WalletDirectoryEntry
import com.example.data.model.WalletTransfer
import com.example.data.repository.AuthRepository
import com.example.data.repository.WalletRepository
import com.example.util.Formatters
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletViewModel(
    val walletRepository: WalletRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val tag = "DelivioWalletVM"

    val currentUser: StateFlow<User?> = authRepository.currentUser

    // Transaction History Filter
    private val _selectedFilter = MutableStateFlow(WalletActivityCategory.ALL)
    val selectedFilter: StateFlow<WalletActivityCategory> = _selectedFilter.asStateFlow()

    // Activity Items from unified activity stream
    val activityItems: StateFlow<List<WalletActivityItem>> = currentUser
        .flatMapLatest { user ->
            val uid = user?.uid ?: ""
            if (uid.isNotBlank()) {
                walletRepository.observeWalletActivity(uid)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Activity Items
    val filteredActivityItems: StateFlow<List<WalletActivityItem>> = combine(
        activityItems,
        selectedFilter
    ) { items, filter ->
        if (filter == WalletActivityCategory.ALL) {
            items
        } else {
            items.filter { it.category == filter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Recent Incoming Transfers (for Receive screen)
    val recentIncomingTransfers: StateFlow<List<WalletTransfer>> = currentUser
        .flatMapLatest { user ->
            val uid = user?.uid ?: ""
            if (uid.isNotBlank()) {
                walletRepository.observeRecentIncomingTransfers(uid)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notification toast/snackbar events
    val incomingTransferEvents: SharedFlow<String> = walletRepository.incomingTransferEvents

    // UI Dialog & Sheet States
    var showComingSoonDialog = MutableStateFlow(false)
        private set
    var comingSoonMessage = MutableStateFlow("")
        private set

    // Send Flow State
    var showSendSheet = MutableStateFlow(false)
        private set
    var sendQuery = MutableStateFlow("")
        private set
    var isLookingUpRecipient = MutableStateFlow(false)
        private set
    var recipientFound = MutableStateFlow<WalletDirectoryEntry?>(null)
        private set
    var sendLookupError = MutableStateFlow<String?>(null)
        private set
    var sendAmount = MutableStateFlow("")
        private set
    var sendNote = MutableStateFlow("")
        private set
    var isSendingMoney = MutableStateFlow(false)
        private set
    var sendError = MutableStateFlow<String?>(null)
        private set
    var showSendConfirmDialog = MutableStateFlow(false)
        private set

    // Success Receipt Dialog State
    var lastReceipt = MutableStateFlow<SendMoneyReceipt?>(null)
        private set

    // Receive Flow State
    var showReceiveSheet = MutableStateFlow(false)
        private set
    var receiveRequestAmount = MutableStateFlow("")
        private set
    var generatedQrBitmap = MutableStateFlow<Bitmap?>(null)
        private set

    // Scan Flow State
    var showScanSheet = MutableStateFlow(false)
        private set
    var scanRecipient = MutableStateFlow<WalletDirectoryEntry?>(null)
        private set
    var scanLockedAmount = MutableStateFlow<Double?>(null)
        private set
    var scanEditableAmount = MutableStateFlow("")
        private set
    var scanNote = MutableStateFlow("")
        private set
    var isScanPaying = MutableStateFlow(false)
        private set
    var scanError = MutableStateFlow<String?>(null)
        private set
    var showScanConfirmDialog = MutableStateFlow(false)
        private set
    var showManualCodeDialog = MutableStateFlow(false)
        private set
    var manualCodeInput = MutableStateFlow("")
        private set

    private var incomingTransfersListener: ListenerRegistration? = null

    init {
        // Start listening to incoming transfers for notification
        viewModelScope.launch {
            currentUser.collect { user ->
                val uid = user?.uid ?: ""
                incomingTransfersListener?.remove()
                if (uid.isNotBlank()) {
                    incomingTransfersListener = walletRepository.startListeningToIncomingTransfers(uid)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        incomingTransfersListener?.remove()
    }

    // ----------------------------------------------------
    // Actions
    // ----------------------------------------------------

    fun setFilter(filter: WalletActivityCategory) {
        _selectedFilter.value = filter
    }

    fun openComingSoon(actionName: String) {
        comingSoonMessage.value = "Top-ups and bank or e-wallet transfers are coming soon. To add cash now, contact Delivio support."
        showComingSoonDialog.value = true
    }

    fun dismissComingSoon() {
        showComingSoonDialog.value = false
    }

    // ----------------------------------------------------
    // Send Flow
    // ----------------------------------------------------

    fun openSendSheet() {
        sendQuery.value = ""
        recipientFound.value = null
        sendLookupError.value = null
        sendAmount.value = ""
        sendNote.value = ""
        sendError.value = null
        showSendConfirmDialog.value = false
        showSendSheet.value = true
    }

    fun dismissSendSheet() {
        showSendSheet.value = false
        recipientFound.value = null
        sendLookupError.value = null
        sendError.value = null
    }

    fun onSendQueryChanged(query: String) {
        sendQuery.value = query
        sendLookupError.value = null
    }

    fun onSendAmountChanged(amount: String) {
        sendAmount.value = amount
        sendError.value = null
    }

    fun onSendNoteChanged(note: String) {
        sendNote.value = note
    }

    fun lookupRecipient() {
        val query = sendQuery.value.trim()
        if (query.isBlank()) {
            sendLookupError.value = "Please enter a mobile number or email"
            return
        }
        val myUid = currentUser.value?.uid ?: ""

        isLookingUpRecipient.value = true
        sendLookupError.value = null

        viewModelScope.launch {
            val result = walletRepository.lookupRecipient(query)
            isLookingUpRecipient.value = false
            result.onSuccess { entry ->
                if (entry.uid == myUid) {
                    recipientFound.value = null
                    sendLookupError.value = "You cannot send money to yourself"
                } else {
                    recipientFound.value = entry
                    sendLookupError.value = null
                }
            }.onFailure { err ->
                recipientFound.value = null
                sendLookupError.value = err.message ?: "Not found. The person must have a Delivio account"
            }
        }
    }

    fun requestSendConfirmation() {
        val recipient = recipientFound.value
        if (recipient == null) {
            sendError.value = "Please look up a recipient first"
            return
        }
        val amount = sendAmount.value.toDoubleOrNull()
        if (amount == null || amount < 1.0 || amount > 10000.0) {
            sendError.value = "Please enter an amount between ₱1.00 and ₱10,000.00"
            return
        }
        val currentBalance = currentUser.value?.walletBalance ?: 0.0
        if (amount > currentBalance) {
            sendError.value = "Insufficient balance. Your balance is ₱${Formatters.formatCurrency(currentBalance)}"
            return
        }

        sendError.value = null
        showSendConfirmDialog.value = true
    }

    fun dismissSendConfirmDialog() {
        showSendConfirmDialog.value = false
    }

    fun executeSendMoney() {
        val user = currentUser.value ?: return
        val recipient = recipientFound.value ?: return
        val amount = sendAmount.value.toDoubleOrNull() ?: return
        val note = sendNote.value

        showSendConfirmDialog.value = false
        isSendingMoney.value = true
        sendError.value = null

        viewModelScope.launch {
            val result = walletRepository.sendMoney(
                fromUid = user.uid,
                fromName = user.name,
                toUid = recipient.uid,
                toName = recipient.displayName,
                amount = amount,
                note = note,
                method = "send"
            )
            isSendingMoney.value = false
            result.onSuccess { receipt ->
                showSendSheet.value = false
                lastReceipt.value = receipt
            }.onFailure { err ->
                sendError.value = err.localizedMessage ?: err.message ?: "Transfer failed"
            }
        }
    }

    fun dismissReceiptDialog() {
        lastReceipt.value = null
    }

    // ----------------------------------------------------
    // Receive Flow & QR Generation
    // ----------------------------------------------------

    fun openReceiveSheet() {
        receiveRequestAmount.value = ""
        regenerateReceiveQr()
        showReceiveSheet.value = true
    }

    fun dismissReceiveSheet() {
        showReceiveSheet.value = false
    }

    fun onReceiveAmountChanged(amount: String) {
        receiveRequestAmount.value = amount
        regenerateReceiveQr()
    }

    fun regenerateReceiveQr() {
        val user = currentUser.value ?: return
        val uid = user.uid
        val amountParsed = receiveRequestAmount.value.toDoubleOrNull()

        val qrContent = StringBuilder("delivio://pay?uid=$uid")
        if (amountParsed != null && amountParsed > 0) {
            qrContent.append("&amount=$amountParsed")
        }

        viewModelScope.launch(Dispatchers.Default) {
            val bitmap = generateQrBitmap(qrContent.toString(), 512)
            withContext(Dispatchers.Main) {
                generatedQrBitmap.value = bitmap
            }
        }
    }

    private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e(tag, "QR generation failed", e)
            null
        }
    }

    // ----------------------------------------------------
    // Scan Flow
    // ----------------------------------------------------

    fun openScanSheet() {
        scanRecipient.value = null
        scanLockedAmount.value = null
        scanEditableAmount.value = ""
        scanNote.value = ""
        scanError.value = null
        showScanConfirmDialog.value = false
        showManualCodeDialog.value = false
        manualCodeInput.value = ""
        showScanSheet.value = true
    }

    fun dismissScanSheet() {
        showScanSheet.value = false
        scanRecipient.value = null
        scanError.value = null
    }

    fun onQrScanned(rawCode: String) {
        val code = rawCode.trim()
        val myUid = currentUser.value?.uid ?: ""

        if (code.startsWith("000201")) {
            scanError.value = "Paying to banks and e-wallets is coming soon."
            return
        }

        if (!code.startsWith("delivio://pay")) {
            scanError.value = "This is not a Delivio QR."
            return
        }

        try {
            val uri = Uri.parse(code)
            val uid = uri.getQueryParameter("uid")
            val amountStr = uri.getQueryParameter("amount")
            val amountDouble = amountStr?.toDoubleOrNull()

            if (uid.isNullOrBlank()) {
                scanError.value = "This is not a Delivio QR."
                return
            }

            if (uid == myUid) {
                scanError.value = "You cannot send money to yourself."
                return
            }

            viewModelScope.launch {
                val result = walletRepository.lookupRecipient(uid)
                result.onSuccess { entry ->
                    scanRecipient.value = entry
                    scanLockedAmount.value = if (amountDouble != null && amountDouble > 0) amountDouble else null
                    scanEditableAmount.value = if (amountDouble != null && amountDouble > 0) amountDouble.toString() else ""
                    scanError.value = null
                }.onFailure { err ->
                    scanError.value = err.message ?: "Not found. The person must have a Delivio account"
                }
            }
        } catch (e: Exception) {
            scanError.value = "This is not a Delivio QR."
        }
    }

    fun openManualCodeDialog() {
        manualCodeInput.value = ""
        showManualCodeDialog.value = true
    }

    fun dismissManualCodeDialog() {
        showManualCodeDialog.value = false
    }

    fun submitManualCode(code: String) {
        showManualCodeDialog.value = false
        onQrScanned("delivio://pay?uid=${code.trim()}")
    }

    fun onScanAmountChanged(amount: String) {
        if (scanLockedAmount.value == null) {
            scanEditableAmount.value = amount
            scanError.value = null
        }
    }

    fun onScanNoteChanged(note: String) {
        scanNote.value = note
    }

    fun requestScanPayConfirmation() {
        val recipient = scanRecipient.value
        if (recipient == null) {
            scanError.value = "No recipient scanned"
            return
        }
        val amount = scanLockedAmount.value ?: scanEditableAmount.value.toDoubleOrNull()
        if (amount == null || amount < 1.0 || amount > 10000.0) {
            scanError.value = "Please enter an amount between ₱1.00 and ₱10,000.00"
            return
        }
        val currentBalance = currentUser.value?.walletBalance ?: 0.0
        if (amount > currentBalance) {
            scanError.value = "Insufficient balance. Your balance is ₱${Formatters.formatCurrency(currentBalance)}"
            return
        }

        scanError.value = null
        showScanConfirmDialog.value = true
    }

    fun dismissScanConfirmDialog() {
        showScanConfirmDialog.value = false
    }

    fun executeScanPay() {
        val user = currentUser.value ?: return
        val recipient = scanRecipient.value ?: return
        val amount = scanLockedAmount.value ?: scanEditableAmount.value.toDoubleOrNull() ?: return
        val note = scanNote.value

        showScanConfirmDialog.value = false
        isScanPaying.value = true
        scanError.value = null

        viewModelScope.launch {
            val result = walletRepository.sendMoney(
                fromUid = user.uid,
                fromName = user.name,
                toUid = recipient.uid,
                toName = recipient.displayName,
                amount = amount,
                note = note,
                method = "qr"
            )
            isScanPaying.value = false
            result.onSuccess { receipt ->
                showScanSheet.value = false
                lastReceipt.value = receipt
            }.onFailure { err ->
                scanError.value = err.localizedMessage ?: err.message ?: "Payment failed"
            }
        }
    }
}
