# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android boilerplate in Kotlin: MVVM + Clean Architecture, single Gradle module (`:app`), XML layouts + ViewBinding, **multi-Activity** navigation: every screen is an Activity; Fragments are only for bottom sheets and child views inside a screen, and dialogs are plain `AppCompatDialog`s (no Navigation Component, no Compose). Stack: Hilt, Retrofit 3 + OkHttp 4 + kotlinx-serialization, Room 2.x, Coroutines/Flow, LiveData. The sample feature is a Posts list/detail backed by `https://jsonplaceholder.typicode.com/`. All dependency versions live in `gradle/libs.versions.toml`.

## Commands

Windows host. In a normal terminal `gradlew.bat <task>` works. From agent shells, `gradlew.bat` fails (PowerShell/Git Bash mangle its empty `-classpath ""` argument), so invoke the wrapper jar directly from PowerShell — and don't pass `-D...` args, which PowerShell 5.1 also mangles:

```powershell
java -jar gradle\wrapper\gradle-wrapper.jar assembleDebug --console=plain
java -jar gradle\wrapper\gradle-wrapper.jar testDebugUnitTest --console=plain
java -jar gradle\wrapper\gradle-wrapper.jar testDebugUnitTest --tests "*PostListViewModelTest*" --console=plain
java -jar gradle\wrapper\gradle-wrapper.jar lintDebug --console=plain
```

Lint findings are only in the report (`app/build/reports/lint-results-debug.sarif` / `.html`), not the console. Test results: `app/build/test-results/testDebugUnitTest/*.xml`. No emulator/AVD is configured on this machine, so UI behavior can't be verified by running the app here — say so rather than claiming it works.

## Git commits and pushes

Commits and pushes are made in the owner's name only (GitHub account `CallmeDunno`):

- Author/committer come from the existing git config (`CallmeDunno <dungworkit@gmail.com>`). Check `git config user.name` / `user.email` before committing; don't override them.
- Do **not** add a `Co-Authored-By: Claude …` trailer (or any other AI attribution) to commit messages or PR descriptions — GitHub renders it as a co-author. This overrides default attribution guidance.
- `origin` is `git@github.com-personal:CallmeDunno/android_architecture_base.git` (the `github.com-personal` SSH alias authenticates as `CallmeDunno`).

## Build configuration gotchas (AGP 9.3)

- AGP 9 has **built-in Kotlin**. Do not apply `org.jetbrains.kotlin.android` (build fails) and do not use `android.kotlinOptions {}`. Kotlin `jvmTarget` defaults to `compileOptions.targetCompatibility` (11). The "Kotlin does not yet support 25 JDK target" warning is benign — bytecode is verified to be Java 11.
- Hilt must be **≥ 2.59**; older Hilt Gradle plugins fail on AGP 9 with "Android BaseExtension not found".
- `android.disallowKotlinSourceSets=false` in `gradle.properties` is required because KSP still registers generated sources via `kotlin.sourceSets` (google/ksp#2729). Remove only once KSP is fixed.
- Annotation processing is KSP only (Room, Hilt) — no kapt.
- Core library desugaring is on (`isCoreLibraryDesugaringEnabled` + `desugar_jdk_libs`) so `java.time` works on minSdk 24–25 (`DateTimeUtils`). Keep it while minSdk < 26.

## Architecture

Package-layered under `com.example.codebase`; dependencies point inward: `presentation → domain ← data`, wired by `di`.

- `domain/` is pure Kotlin: models, repository interfaces, use cases (thin `operator fun invoke` pass-throughs). `Resource<T>` (Loading/Success/Error, where `Error` can carry stale data) lives in `domain/util` because repository interfaces return it — never move it into `presentation`.
- `data/` owns both DTOs (`remote/dto`, `@Serializable`) and Room entities (`local`); `data/mapper` extension functions convert DTO → Entity → domain model. DTOs/entities must not leak outside `data`/`di`.
- `di/` has three `SingletonComponent` modules: network (OkHttp w/ logging, Retrofit, `ApiService`), database (`AppDatabase`, DAOs), repository (`@Binds` impl → interface).

### Online-first repository flow

Data policy is **online-first**: the network is always tried first, and Room is only read as a fallback when the request fails. `PostRepositoryImpl` methods are `flow {}` builders: emit `Loading` → fetch from network and write to Room → `emitAll` the live Room Flow (Room stays the single source of truth for what the UI shows). On any failure: read Room once (`.first()`), emit `Error(message, cachedData)` — `data` is `null` if nothing is cached — and `return@flow`. No cached data is emitted before the network result, so there is no `forceRefresh` parameter; pull-to-refresh simply calls `loadPosts()` again. Consequences to preserve when editing:

- On success the returned Flow **never completes** (it ends in a Room Flow). Any ViewModel that re-invokes a use case must cancel the previous collection (`loadJob?.cancel()` before `launchIn`), otherwise collectors accumulate.
- The network block catches `Exception`, but `CancellationException` is caught first and rethrown — required because ViewModels cancel in-flight loads. Keep that ordering. Error text comes from `toErrorMessage()` in the same file.
- Keep `emit` calls outside the `try` to avoid violating Flow exception transparency.
- `PostDao.clearAndInsertAll` is a `@Transaction` default interface method; Room generates the transaction wrapper for it.

### Presentation

Each screen is an `@AndroidEntryPoint` Activity + `@HiltViewModel` + a `*UiState` data class. ViewModels extend `BaseViewModel<S>`, which holds the private `MutableStateFlow` and exposes both `uiState: StateFlow` and `uiStateLiveData = uiState.asLiveData()`; subclasses change state only via `setState { copy(...) }`. By design `PostListActivity` (launcher) collects the StateFlow with `collectWhenStarted` (`repeatOnLifecycle(STARTED)`), while `PostDetailActivity` observes the LiveData — both consumption styles are intentionally demonstrated. Screens are opened via `Intent` extras (`PostDetailActivity.EXTRA_POST_ID`); the detail Activity calls `viewModel.loadPost(id)` itself rather than using `SavedStateHandle`, skipping the call if the ViewModel already has data or is loading.

### Base classes (`com.example.codebase.base`)

Every new UI class **must** extend its base; don't hand-roll binding/inset/state boilerplate.

| Base | Extend for | Subclass provides |
|---|---|---|
| `BaseActivity<VB>(XxxBinding::inflate)` | screens | `initView`, optional `initListeners` / `observeData` / `applyWindowInsets` |
| `BaseFragment<VB>(XxxBinding::inflate)` | child views in a screen | same hooks |
| `BaseDialog<VB>(context, XxxBinding::inflate)` (`AppCompatDialog`, no Fragment) | dialogs with static content | `initView`, optional `initListeners`; `dialogWidthRatio`, `isCancelableByUser`. Data and callbacks via constructor. Window background is transparent, so the layout root draws its own background |
| `BaseBottomSheet<VB>` (`BottomSheetDialogFragment`) | bottom sheets | same hooks; `expandOnShow` |
| `BaseListAdapter<T, VB>(diffCallback)` | RecyclerView lists — **the default** (`ListAdapter`) | `createBinding(…, viewType)`, `bind`; optional `getItemViewType`, `onViewHolderCreated` (listeners), `bindPayloads` |
| `BaseAdapter<T, VB>(diffCallback)` | lists with `ItemTouchHelper` drag & drop (own background diff + synchronous `moveItem`) | same hooks as `BaseListAdapter` |
| `BaseDiffCallback<T> { it.id }` | the adapter's DiffUtil callback | an id selector; `T` must be a data class (contents compared with `==`). Subclass only for custom content/payload rules |
| `BaseViewModel<S>(initialState)` | ViewModels | own `loadJob` + `setState` |

- Hooks always run `initView → initListeners → observeData` (Activity: in `onCreate` after `setContentView` + insets; Fragment-based: in `onViewCreated`; `BaseDialog`: `initView → initListeners` on first `show()`). Don't override `onCreate`/`onCreateView` for setup.
- `BaseDialog` is deliberately lifecycle-free: no ViewModel, no Flow collection, not restored after rotation or process death. It dismisses itself when the host Activity (the `context` passed in) is destroyed. A dialog that must survive rotation or observe state should be a `BaseBottomSheet`, or a screen-level UI state that re-shows the dialog.
- `BaseActivity` pads `binding.root` by the system bars (edge-to-edge is always on), so the layout root is the inset target.
- Fragment-based bases null the binding in `onDestroyView`; `binding` throws outside the view lifecycle. Their `collectWhenStarted` uses `viewLifecycleOwner`.
- Adapter rules (`BaseListAdapter` and `BaseAdapter` share hooks and `BaseViewHolder`):
  - Pick `BaseListAdapter` unless the list is draggable. `BaseAdapter` is deliberately **not** a `ListAdapter`: `ItemTouchHelper` needs `notifyItemMoved` synchronously during a drag, and `ListAdapter` can only change its list through an async diff. `BaseAdapter.moveItem(from, to)` covers that and discards any diff still in flight.
  - Multiple view types: `VB = ViewBinding` + `when (binding)`; ids from `BaseDiffCallback` must be unique across row types.
  - Clicks are not built in. Set item and child-view listeners once in `onViewHolderCreated` and resolve the item with `getItemOrNull(holder)` at click time; don't create listeners in `bind` or capture `position`.
  - Drag & drop: call `moveItem` from `onMove`, then hand `currentList` to the ViewModel when the drag ends (`clearView`), so the next state emission doesn't restore the old order.
  - `RecyclerView.Adapter` can't be instantiated in JVM unit tests (its observer list comes from the stubbed `android.jar`, so any `notify*` call throws a NPE). Keep adapter logic that needs testing in pure functions, as with `List.withItemMoved` in `BaseAdapter.kt`; the adapter itself isn't tested without Robolectric.
- `base/` is presentation-only: `domain` and `data` must not import it.

### Utils (`com.example.codebase.utils`)

Stateless helpers, one `object XxxUtils` per concern, with functions that take a `Context`. Reuse them instead of writing storage, permission, network or formatting code inside a feature.

| Utils | Covers |
|---|---|
| `FileUtils`, `FormatUtils`, `DateTimeUtils` | Pure JVM. Atomic writes, safe path resolution, file names; size/number/duration/percent formatting; `java.time` format/parse on epoch millis |
| `AssetUtils`, `InternalStorageUtils`, `CacheUtils`, `ExternalStorageUtils` | Assets (incl. JSON), `filesDir`, `cacheDir` (size/clear/temp files), app-specific external dirs, MediaStore saves (Pictures, Downloads) |
| `PrefsUtils`, `ThemeUtils` | `app_prefs` SharedPreferences (typed values, JSON, `observe` Flow); light/dark/system mode saved and re-applied in `CodebaseApp`, opt-in dynamic colors |
| `NetworkUtils`, `PermissionUtils`, `IntentUtils` | Connectivity state + `observeConnectivity` Flow; check-then-request permissions; URLs, settings screens, sharing text/files, email, dialer, Play Store |
| `KeyboardUtils`, `ScreenUtils` | Show/hide the soft keyboard; dp/sp/px, window size, system bar heights, tablet/landscape |

- **Dependencies.** `presentation`, `data` and `di` may use utils. `domain` must not, since it stays pure Kotlin. `utils` must not import `base/`. Don't call Context-based utils from a ViewModel: call them from the Activity or Fragment, or wrap them in a repository.
- **Testability.** Logic that doesn't need Android lives in the pure objects, or in `internal` top-level functions with explicit inputs (`networkTypeOf`, `applicablePermissions(permissions, sdkInt)`, `mediaAccessOf`, `normalizeUrl`, `Json.decodeOrNull`). These are tested in `app/src/test/.../utils`, and public functions pass `Build.VERSION.SDK_INT` into them. Objects must not initialize properties with Android calls; loading such a class in a JVM test would crash.
- **I/O.** Context-based file functions are `suspend` and switch to `Dispatchers.IO`. `FileUtils` blocks, so call it off the main thread. Failures return `null` or `false`. Only specific exceptions are caught (`IOException`, `SecurityException`, `IllegalArgumentException` incl. `SerializationException`, …), never `Exception`.
- **`NetworkUtils`** is for UI, such as an offline banner. Don't use it to skip requests in repositories: the policy is online-first, so the request itself decides.
- **`PermissionUtils.request(context, …) { onGranted }`**
  - Checks first and only asks for the permissions that are missing.
  - Can be called at any time, because it registers on `activityResultRegistry` without a `LifecycleOwner`.
  - There is no denial callback. A result is dropped if the Activity is recreated while the dialog is showing.
  - Permissions that don't apply to the running API count as granted, e.g. `POST_NOTIFICATIONS` below 33 or `WRITE_EXTERNAL_STORAGE` from 29 (`PERMISSION_SDK_RANGES`). Every permission must still be declared in the manifest by the feature that uses it.
  - For photos, use `requestMediaImages` so that partial access on API 34+ still continues.
- **FileProvider** (`${applicationId}.fileprovider`) exposes only `filesDir/shared` and `cacheDir/shared` (`res/xml/file_paths.xml`). Put files in `InternalStorageUtils.sharedDir` or `CacheUtils.sharedCacheDir` before calling `IntentUtils.shareFile`; don't widen the paths.
- **`ExternalStorageUtils`** saves to shared collections through MediaStore on API 29+ without a permission. On API 24–28 it needs `WRITE_EXTERNAL_STORAGE`, declared with `maxSdkVersion="28"`.
- **`PrefsUtils`** is plain XML: never store tokens or secrets in it.

## Unit test conventions

- ViewModel tests need both `InstantTaskExecutorRule` (the eager `asLiveData()` in the ViewModel constructor touches the main looper, even if the test ignores LiveData) and `MainDispatcherRule` (`app/src/test/.../util`).
- Pass the rule's dispatcher to `runTest(mainDispatcherRule.testDispatcher)` so `viewModelScope` shares the test scheduler. To assert intermediate states, separate emissions in fake flows with `delay(...)` and step with `runCurrent()` / `advanceUntilIdle()` — `yield()` is not enough, `runCurrent()` drains zero-delay tasks.
- `asLiveData()` only collects with an active observer: call `observeForever {}` before asserting `uiStateLiveData.value`.
- Repository tests use an in-memory `FakePostDao` backed by `MutableStateFlow` plus mockk for `ApiService` (no Robolectric). Because successful flows never complete, use `cancelAndIgnoreRemainingEvents()` in Turbine, not `awaitComplete()`.
- Top-level `private` helper classes in different test files of the same package still clash at JVM level — give fakes distinct names.
- Utils tests cover only the pure parts (see "Utils"). File tests use JUnit `TemporaryFolder` and must also pass on this Windows host, where, for example, `File.renameTo` can't replace an existing file.
