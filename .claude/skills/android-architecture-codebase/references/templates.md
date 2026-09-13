# Layer templates

Copy-ready skeletons mirroring the Posts feature. Replace `Xxx` / `xxx` / `<feature>` and drop what the task doesn't need. Package root is `com.example.codebase`.

## Contents

1. Domain — model, repository interface, use case
2. Remote — DTO, ApiService methods
3. Local — entity, DAO, database registration, DAO provider
4. Mappers
5. Repository impl (online-first) + binding
6. Presentation — UiState, ViewModel, Activity (StateFlow), Activity (LiveData + extra), layout root, manifest
7. Tests — repository, ViewModel

---

## 1. Domain

```kotlin
// domain/model/Xxx.kt
data class Xxx(
    val id: Int,
    val name: String
)
```

```kotlin
// domain/repository/XxxRepository.kt
interface XxxRepository {
    fun getXxxs(): Flow<Resource<List<Xxx>>>
    fun getXxxById(id: Int): Flow<Resource<Xxx>>
}
```

```kotlin
// domain/usecase/GetXxxsUseCase.kt
class GetXxxsUseCase @Inject constructor(
    private val repository: XxxRepository
) {
    operator fun invoke(): Flow<Resource<List<Xxx>>> = repository.getXxxs()
}
```

## 2. Remote

```kotlin
// data/remote/dto/XxxDto.kt
@Serializable
data class XxxDto(
    val id: Int,
    val name: String
    // @SerialName("created_at") val createdAt: String  <- when JSON key differs
)
```

```kotlin
// data/remote/ApiService.kt — add to the existing interface
@GET("xxxs")
suspend fun getXxxs(): List<XxxDto>

@GET("xxxs/{id}")
suspend fun getXxx(@Path("id") id: Int): XxxDto
```

## 3. Local

```kotlin
// data/local/XxxEntity.kt
@Entity(tableName = "xxxs")
data class XxxEntity(
    @PrimaryKey val id: Int,
    val name: String
)
```

```kotlin
// data/local/XxxDao.kt
@Dao
interface XxxDao {

    @Query("SELECT * FROM xxxs")
    fun getAll(): Flow<List<XxxEntity>>

    @Query("SELECT * FROM xxxs WHERE id = :id")
    fun getById(id: Int): Flow<XxxEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<XxxEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: XxxEntity)

    @Query("DELETE FROM xxxs")
    suspend fun clearAll()

    // Full-list endpoints only; never for paginated/filtered responses.
    @Transaction
    suspend fun clearAndInsertAll(items: List<XxxEntity>) {
        clearAll()
        insertAll(items)
    }
}
```

```kotlin
// data/local/AppDatabase.kt — register the entity and decide the version (see SKILL.md "Room schema changes")
@Database(entities = [PostEntity::class, XxxEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun xxxDao(): XxxDao
}
```

```kotlin
// di/DatabaseModule.kt — add alongside providePostDao
@Provides
@Singleton
fun provideXxxDao(appDatabase: AppDatabase): XxxDao = appDatabase.xxxDao()
```

## 4. Mappers

```kotlin
// data/mapper/XxxMappers.kt
fun XxxDto.toEntity(): XxxEntity = XxxEntity(id = id, name = name)

fun XxxEntity.toDomain(): Xxx = Xxx(id = id, name = name)
```

## 5. Repository (online-first) + binding

```kotlin
// data/repository/XxxRepositoryImpl.kt
class XxxRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val xxxDao: XxxDao
) : XxxRepository {

    // Online-first: the network is always tried first; Room is only read as a fallback when it fails.
    override fun getXxxs(): Flow<Resource<List<Xxx>>> = flow {
        emit(Resource.Loading())

        try {
            val remote = apiService.getXxxs()
            xxxDao.clearAndInsertAll(remote.map { it.toEntity() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = xxxDao.getAll().first().map { it.toDomain() }
            emit(Resource.Error(e.toErrorMessage(), cached.ifEmpty { null }))
            return@flow
        }

        emitAll(xxxDao.getAll().map { entities ->
            Resource.Success(entities.map { it.toDomain() }) as Resource<List<Xxx>>
        })
    }

    override fun getXxxById(id: Int): Flow<Resource<Xxx>> = flow {
        emit(Resource.Loading())

        try {
            xxxDao.insert(apiService.getXxx(id).toEntity())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.toErrorMessage(), xxxDao.getById(id).first()?.toDomain()))
            return@flow
        }

        emitAll(xxxDao.getById(id).filterNotNull().map {
            Resource.Success(it.toDomain()) as Resource<Xxx>
        })
    }
}
```

`toErrorMessage()` is `private` in `PostRepositoryImpl.kt`. When a second repository needs it, move it once to an `internal` function in `data/repository/` (for example `ErrorMessages.kt`) instead of copying it.

```kotlin
// di/RepositoryModule.kt — add inside the existing abstract class
@Binds
@Singleton
abstract fun bindXxxRepository(impl: XxxRepositoryImpl): XxxRepository
```

## 6. Presentation

```kotlin
// presentation/<feature>/XxxListUiState.kt
data class XxxListUiState(
    val items: List<Xxx> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

```kotlin
// presentation/<feature>/XxxListViewModel.kt
@HiltViewModel
class XxxListViewModel @Inject constructor(
    private val getXxxsUseCase: GetXxxsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(XxxListUiState())
    val uiState: StateFlow<XxxListUiState> = _uiState.asStateFlow()
    val uiStateLiveData: LiveData<XxxListUiState> = uiState.asLiveData()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        // The repository flow never completes, so the previous collection must be cancelled.
        loadJob?.cancel()
        loadJob = getXxxsUseCase().onEach { resource ->
            _uiState.update { current ->
                when (resource) {
                    is Resource.Loading -> current.copy(isLoading = true)
                    is Resource.Success -> current.copy(isLoading = false, items = resource.data, error = null)
                    is Resource.Error -> current.copy(
                        isLoading = false,
                        error = resource.message,
                        items = resource.data ?: current.items
                    )
                }
            }
        }.launchIn(viewModelScope)
    }
}
```

```kotlin
// presentation/<feature>/XxxListActivity.kt — default style: collect StateFlow
@AndroidEntryPoint
class XxxListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityXxxListBinding
    private val viewModel: XxxListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityXxxListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: XxxListUiState) { /* bind views; text from R.string */ }
}
```

```kotlin
// presentation/<feature>/XxxDetailActivity.kt — ID extra + LiveData variant
@AndroidEntryPoint
class XxxDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_XXX_ID = "extra_xxx_id"
    }

    private val viewModel: XxxDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... binding + insets as above ...
        viewModel.uiStateLiveData.observe(this) { render(it) }

        // Skip on rotation (ViewModel survived), reload after process death (fresh ViewModel).
        val alreadyLoadedOrLoading = viewModel.uiState.value.let { it.item != null || it.isLoading }
        if (!alreadyLoadedOrLoading) {
            val id = intent.getIntExtra(EXTRA_XXX_ID, -1)
            check(id != -1) { "XxxDetailActivity requires EXTRA_XXX_ID" }
            viewModel.load(id)
        }
    }
}

// Caller:
startActivity(Intent(this, XxxDetailActivity::class.java).putExtra(XxxDetailActivity.EXTRA_XXX_ID, item.id))
```

```xml
<!-- res/layout/activity_xxx_list.xml — root requirements -->
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".presentation.xxxlist.XxxListActivity">
    <!-- children: camelCase ids (recyclerXxxs, swipeRefresh, textError, progressBar) -->
</androidx.constraintlayout.widget.ConstraintLayout>
```

```xml
<!-- AndroidManifest.xml, inside <application> -->
<activity
    android:name=".presentation.xxxlist.XxxListActivity"
    android:exported="false"
    android:windowSoftInputMode="adjustResize" />
```

## 7. Tests

```kotlin
// test/.../data/repository/XxxRepositoryImplTest.kt
private class FakeXxxDao : XxxDao {   // unique name per package
    private val state = MutableStateFlow<List<XxxEntity>>(emptyList())
    fun setInitial(items: List<XxxEntity>) { state.value = items }
    override fun getAll(): Flow<List<XxxEntity>> = state
    override fun getById(id: Int): Flow<XxxEntity?> = state.map { list -> list.find { it.id == id } }
    override suspend fun insertAll(items: List<XxxEntity>) {
        val merged = state.value.associateBy { it.id }.toMutableMap()
        items.forEach { merged[it.id] = it }
        state.value = merged.values.toList()
    }
    override suspend fun insert(item: XxxEntity) = insertAll(listOf(item))
    override suspend fun clearAll() { state.value = emptyList() }
}

class XxxRepositoryImplTest {

    @Test
    fun `falls back to cached data when network fails`() = runTest {
        val dao = FakeXxxDao().apply { setInitial(listOf(XxxEntity(1, "cached"))) }
        val api = mockk<ApiService>()
        coEvery { api.getXxxs() } throws IOException("no connection")

        XxxRepositoryImpl(api, dao).getXxxs().test {
            assertEquals(Resource.Loading<Any>(), awaitItem())
            val error = awaitItem() as Resource.Error
            assertEquals(listOf(Xxx(1, "cached")), error.data)
            awaitComplete()                       // error path completes
        }
    }

    @Test
    fun `network success emits remote data`() = runTest {
        val api = mockk<ApiService>()
        coEvery { api.getXxxs() } returns listOf(XxxDto(1, "remote"))

        XxxRepositoryImpl(api, FakeXxxDao()).getXxxs().test {
            assertEquals(Resource.Loading<Any>(), awaitItem())
            assertEquals(listOf(Xxx(1, "remote")), (awaitItem() as Resource.Success).data)
            cancelAndIgnoreRemainingEvents()      // success path never completes
        }
    }

    @Test
    fun `CancellationException propagates`() = runTest {
        val api = mockk<ApiService>()
        coEvery { api.getXxxs() } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { XxxRepositoryImpl(api, FakeXxxDao()).getXxxs().toList() }
        }
    }
}
```

```kotlin
// test/.../presentation/<feature>/XxxListViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class XxxListViewModelTest {

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `Loading then Success`() = runTest(mainDispatcherRule.testDispatcher) {
        val useCase = mockk<GetXxxsUseCase>()
        every { useCase() } returns flow {
            emit(Resource.Loading())
            delay(1)                              // not yield(): runCurrent() would drain it
            emit(Resource.Success(listOf(Xxx(1, "a"))))
        }

        val viewModel = XxxListViewModel(useCase)
        assertEquals(XxxListUiState(), viewModel.uiState.value)

        runCurrent()
        assertEquals(XxxListUiState(isLoading = true), viewModel.uiState.value)

        advanceUntilIdle()
        assertEquals(listOf(Xxx(1, "a")), viewModel.uiState.value.items)
    }

    @Test
    fun `reload cancels previous collection`() = runTest(mainDispatcherRule.testDispatcher) {
        val active = AtomicInteger(0)
        val useCase = mockk<GetXxxsUseCase>()
        every { useCase() } returns MutableStateFlow<Resource<List<Xxx>>>(Resource.Success(emptyList()))
            .onStart { active.incrementAndGet() }
            .onCompletion { active.decrementAndGet() }

        val viewModel = XxxListViewModel(useCase)
        advanceUntilIdle()
        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, active.get())
    }
}
```

For `uiStateLiveData` assertions, call `viewModel.uiStateLiveData.observeForever {}` before `advanceUntilIdle()`.
