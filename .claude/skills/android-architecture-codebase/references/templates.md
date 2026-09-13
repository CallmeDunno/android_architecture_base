# Layer templates

Copy-ready skeletons mirroring the Posts feature. Replace `Xxx` / `xxx` / `<feature>` and drop what the task doesn't need. Package root is `com.example.codebase`.

## Contents

1. Domain — model, repository interface, use case
2. Remote — DTO, ApiService methods
3. Local — entity, DAO, database registration, DAO provider
4. Mappers
5. Repository impl (online-first) + binding
6. Presentation — UiState, ViewModel, Activity (StateFlow), Activity (LiveData + extra), Adapter, Fragment, Dialog, Bottom sheet, layout root, manifest
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
) : BaseViewModel<XxxListUiState>(XxxListUiState()) {   // provides uiState + uiStateLiveData

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        // The repository flow never completes, so the previous collection must be cancelled.
        loadJob?.cancel()
        loadJob = getXxxsUseCase().onEach { resource ->
            setState {
                when (resource) {
                    is Resource.Loading -> copy(isLoading = true)
                    is Resource.Success -> copy(isLoading = false, items = resource.data, error = null)
                    is Resource.Error -> copy(
                        isLoading = false,
                        error = resource.message,
                        items = resource.data ?: items
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
class XxxListActivity : BaseActivity<ActivityXxxListBinding>(ActivityXxxListBinding::inflate) {
    // BaseActivity already did: enableEdgeToEdge, inflate + setContentView, system bar insets on binding.root

    private val viewModel: XxxListViewModel by viewModels()
    private val adapter = XxxAdapter(onItemClick = ::openDetail)

    override fun initView(savedInstanceState: Bundle?) {
        binding.recyclerXxxs.layoutManager = LinearLayoutManager(this)
        binding.recyclerXxxs.adapter = adapter
    }

    override fun initListeners() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }
    }

    override fun observeData() {
        viewModel.uiState.collectWhenStarted(::render)
    }

    private fun render(state: XxxListUiState) { /* bind views; text from R.string */ }

    private fun openDetail(item: Xxx) { /* see caller below */ }
}
```

```kotlin
// presentation/<feature>/XxxDetailActivity.kt — ID extra + LiveData variant
@AndroidEntryPoint
class XxxDetailActivity : BaseActivity<ActivityXxxDetailBinding>(ActivityXxxDetailBinding::inflate) {

    companion object {
        const val EXTRA_XXX_ID = "extra_xxx_id"
    }

    private val viewModel: XxxDetailViewModel by viewModels()

    override fun initView(savedInstanceState: Bundle?) {
        // Skip on rotation (ViewModel survived), reload after process death (fresh ViewModel).
        val alreadyLoadedOrLoading = viewModel.uiState.value.let { it.item != null || it.isLoading }
        if (!alreadyLoadedOrLoading) {
            val id = intent.getIntExtra(EXTRA_XXX_ID, -1)
            check(id != -1) { "XxxDetailActivity requires EXTRA_XXX_ID" }
            viewModel.load(id)
        }
    }

    override fun observeData() {
        viewModel.uiStateLiveData.observe(this, ::render)
    }

    private fun render(state: XxxDetailUiState) { /* ... */ }
}

// Caller:
startActivity(Intent(this, XxxDetailActivity::class.java).putExtra(XxxDetailActivity.EXTRA_XXX_ID, item.id))
```

```kotlin
// presentation/<feature>/XxxAdapter.kt — single view type, item + child click, drag handle.
// BaseAdapter because the list is draggable; without drag & drop extend BaseListAdapter (identical hooks)
// and drop onStartDrag / the touch listener.
class XxxAdapter(
    private val onItemClick: (Xxx) -> Unit,
    private val onFavoriteClick: (Xxx) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : BaseAdapter<Xxx, ItemXxxBinding>(BaseDiffCallback { it.id }) {   // Xxx must be a data class

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): ItemXxxBinding =
        ItemXxxBinding.inflate(inflater, parent, false)

    // Listeners are created once per ViewHolder; the item is resolved at click time.
    @SuppressLint("ClickableViewAccessibility") // the handle only starts a drag, it has no click action
    override fun onViewHolderCreated(holder: BaseViewHolder<ItemXxxBinding>, viewType: Int) {
        holder.binding.root.setOnClickListener { getItemOrNull(holder)?.let(onItemClick) }
        holder.binding.buttonFavorite.setOnClickListener { getItemOrNull(holder)?.let(onFavoriteClick) }
        holder.binding.imageDragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
            false
        }
    }

    override fun bind(binding: ItemXxxBinding, item: Xxx, position: Int) {
        binding.textName.text = item.name
    }
}
```

```kotlin
// presentation/<feature>/XxxRowAdapter.kt — several view types: VB = ViewBinding (no drag → BaseListAdapter)
sealed interface XxxRow {
    data class Header(val title: String) : XxxRow
    data class Item(val xxx: Xxx) : XxxRow
}

class XxxRowAdapter(
    private val onItemClick: (Xxx) -> Unit
) : BaseListAdapter<XxxRow, ViewBinding>(
    BaseDiffCallback { row ->                                   // ids must be unique across row types
        when (row) {
            is XxxRow.Header -> "header_${row.title}"
            is XxxRow.Item -> "item_${row.xxx.id}"
        }
    }
) {
    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is XxxRow.Header -> TYPE_HEADER
        is XxxRow.Item -> TYPE_ITEM
    }

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): ViewBinding = when (viewType) {
        TYPE_HEADER -> ItemXxxHeaderBinding.inflate(inflater, parent, false)
        else -> ItemXxxBinding.inflate(inflater, parent, false)
    }

    override fun onViewHolderCreated(holder: BaseViewHolder<ViewBinding>, viewType: Int) {
        if (viewType == TYPE_ITEM) {
            holder.binding.root.setOnClickListener {
                (getItemOrNull(holder) as? XxxRow.Item)?.let { onItemClick(it.xxx) }
            }
        }
    }

    override fun bind(binding: ViewBinding, item: XxxRow, position: Int) {
        when {
            binding is ItemXxxHeaderBinding && item is XxxRow.Header -> binding.textTitle.text = item.title
            binding is ItemXxxBinding && item is XxxRow.Item -> binding.textName.text = item.xxx.name
        }
    }
}
```

```kotlin
// presentation/<feature>/XxxDragCallback.kt — ItemTouchHelper wiring
class XxxDragCallback(
    private val adapter: BaseAdapter<Xxx, *>,
    private val onDragFinished: (List<Xxx>) -> Unit
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    private var moved = false

    override fun isLongPressDragEnabled(): Boolean = false   // true to drag by long press instead of a handle

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
        adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition).also { moved = moved || it }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (moved) onDragFinished(adapter.currentList)          // persist the order in the ViewModel
        moved = false
    }
}

// Activity:
// private val itemTouchHelper by lazy { ItemTouchHelper(XxxDragCallback(adapter, viewModel::onReordered)) }
// private val adapter = XxxAdapter(::openDetail, viewModel::toggleFavorite, onStartDrag = { itemTouchHelper.startDrag(it) })
// initView: itemTouchHelper.attachToRecyclerView(binding.recyclerXxxs)
```
// Custom rules (e.g. ignore a timestamp, partial rebind): subclass BaseDiffCallback and override
// areContentsTheSame / getChangePayload instead of writing a new DiffUtil.ItemCallback from scratch.
```

```kotlin
// presentation/<feature>/XxxFragment.kt — child view hosted inside an Activity
@AndroidEntryPoint   // needed for Hilt injection / hiltViewModel-backed viewModels(); the host Activity must be @AndroidEntryPoint too
class XxxFragment : BaseFragment<FragmentXxxBinding>(FragmentXxxBinding::inflate) {

    private val viewModel: XxxViewModel by viewModels()          // or activityViewModels() to share with the host

    override fun initView(savedInstanceState: Bundle?) { /* binding is valid from here until onDestroyView */ }

    override fun observeData() {
        viewModel.uiState.collectWhenStarted(::render)            // runs on viewLifecycleOwner
    }

    private fun render(state: XxxUiState) { /* ... */ }
}
```

```kotlin
// presentation/<feature>/XxxDialog.kt — static content; layout root must set its own background (window is transparent)
class XxxDialog(
    context: Context,
    private val title: String,
    private val message: String,
    private val onConfirm: () -> Unit
) : BaseDialog<DialogXxxBinding>(context, DialogXxxBinding::inflate) {

    override val isCancelableByUser = false

    override fun initView() {
        binding.textTitle.text = title
        binding.textMessage.text = message
    }

    override fun initListeners() {
        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonConfirm.setOnClickListener {
            onConfirm()
            dismiss()
        }
    }
}

// Caller (Activity; pass the Activity as context so the dialog dismisses itself when it is destroyed):
// XxxDialog(this, getString(R.string.xxx_title), getString(R.string.xxx_message), onConfirm = viewModel::delete).show()
// From a Fragment: XxxDialog(requireActivity(), ...).show()
```

```kotlin
// presentation/<feature>/XxxBottomSheet.kt
class XxxBottomSheet : BaseBottomSheet<BottomSheetXxxBinding>(BottomSheetXxxBinding::inflate) {

    companion object {
        const val TAG = "XxxBottomSheet"
    }

    override fun initView(savedInstanceState: Bundle?) { /* ... */ }
}

// Caller: XxxBottomSheet().show(supportFragmentManager, XxxBottomSheet.TAG)
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
