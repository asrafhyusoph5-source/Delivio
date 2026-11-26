package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.repository.AuthRepository
import com.example.data.repository.CartRepository
import com.example.data.repository.FirestoreService
import com.example.data.repository.InboxRepository
import com.example.data.repository.OrderRepository
import com.example.data.repository.PreferenceRepository
import com.example.data.repository.RestaurantRepository
import com.example.data.repository.WalletRepository
import com.example.ui.navigation.DelivioNavGraph
import com.example.ui.theme.DelivioTheme
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CartViewModel
import com.example.viewmodel.InboxViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.OrderViewModel
import com.example.viewmodel.WalletViewModel

class MainActivity : ComponentActivity() {

    private lateinit var preferenceRepository: PreferenceRepository
    private lateinit var firestoreService: FirestoreService
    private lateinit var walletRepository: WalletRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var restaurantRepository: RestaurantRepository
    private lateinit var cartRepository: CartRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var inboxRepository: InboxRepository
    private lateinit var supportRepository: com.example.data.repository.SupportRepository
    private lateinit var chatRepository: com.example.data.repository.ChatRepository
    private lateinit var jobRepository: com.example.data.repository.JobRepository

    private lateinit var authViewModel: AuthViewModel
    private lateinit var mainViewModel: MainViewModel
    private lateinit var cartViewModel: CartViewModel
    private lateinit var orderViewModel: OrderViewModel
    private lateinit var walletViewModel: WalletViewModel
    private lateinit var inboxViewModel: InboxViewModel
    private lateinit var supportViewModel: com.example.viewmodel.SupportViewModel
    private lateinit var chatViewModel: com.example.viewmodel.ChatViewModel
    private lateinit var jobFairViewModel: com.example.viewmodel.JobFairViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.util.OsmConfig.init(applicationContext)

        // Configure Coil with OkHttpClient network engine
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val imageLoader = coil.ImageLoader.Builder(applicationContext)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
        coil.Coil.setImageLoader(imageLoader)

        // Initialize Firestore Service and repositories
        preferenceRepository = PreferenceRepository(applicationContext)
        firestoreService = FirestoreService()
        walletRepository = WalletRepository(firestoreService)
        authRepository = AuthRepository(preferenceRepository, firestoreService, walletRepository)
        restaurantRepository = RestaurantRepository(firestoreService, preferenceRepository)
        cartRepository = CartRepository()
        orderRepository = OrderRepository(firestoreService, preferenceRepository)
        inboxRepository = InboxRepository()
        supportRepository = com.example.data.repository.SupportRepository(firestoreService)
        val defaultFirestore = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (_: Exception) { null }
        chatRepository = com.example.data.repository.ChatRepository(defaultFirestore)
        jobRepository = com.example.data.repository.JobRepository()

        // Initialize ViewModels
        authViewModel = AuthViewModel(authRepository, preferenceRepository)
        mainViewModel = MainViewModel(preferenceRepository, firestoreService)
        cartViewModel = CartViewModel(cartRepository, orderRepository)
        orderViewModel = OrderViewModel(orderRepository)
        walletViewModel = WalletViewModel(walletRepository, authRepository)
        inboxViewModel = InboxViewModel(inboxRepository, authViewModel)
        supportViewModel = com.example.viewmodel.SupportViewModel(supportRepository)
        chatViewModel = com.example.viewmodel.ChatViewModel(chatRepository)
        jobFairViewModel = com.example.viewmodel.JobFairViewModel(jobRepository)

        // AuthStateListener: Start Firestore listeners after sign-in; stop and clear on sign-out.
        val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                restaurantRepository.loadCityMerchants("puerto-princesa")
                authRepository.restoreSession()
                mainViewModel.setCustomerId(user.uid)
                orderViewModel.setCustomerId(user.uid)
                supportViewModel.setCustomerId(user.uid)
                chatViewModel.setCustomerId(user.uid)
                jobFairViewModel.setCustomerId(user.uid)
                walletRepository.startListeningToIncomingTransfers(user.uid)
            } else {
                restaurantRepository.stopAndClear()
                mainViewModel.setCustomerId("")
                orderViewModel.setCustomerId("")
                chatViewModel.setCustomerId("")
                jobFairViewModel.setCustomerId("")
            }
        }
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        } catch (_: Exception) {}

        com.example.crash.CrashReportManager.refreshReports(applicationContext)

        setContent {
            val isDarkMode by mainViewModel.isDarkMode.collectAsState()
            val authState by authViewModel.uiState.collectAsState()

            DelivioTheme(darkTheme = isDarkMode) {
                // Keep customerId in ViewModels synced with active auth user
                LaunchedEffect(authState.currentUser?.uid) {
                    val uid = authState.currentUser?.uid ?: ""
                    mainViewModel.setCustomerId(uid)
                    orderViewModel.setCustomerId(uid)
                    supportViewModel.setCustomerId(uid)
                    chatViewModel.setCustomerId(uid)
                    jobFairViewModel.setCustomerId(uid)
                }

                DelivioNavGraph(
                    authViewModel = authViewModel,
                    mainViewModel = mainViewModel,
                    cartViewModel = cartViewModel,
                    orderViewModel = orderViewModel,
                    walletViewModel = walletViewModel,
                    inboxViewModel = inboxViewModel,
                    supportViewModel = supportViewModel,
                    chatViewModel = chatViewModel,
                    restaurantRepository = restaurantRepository,
                    jobFairViewModel = jobFairViewModel
                )
            }
        }
    }
}
