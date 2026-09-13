---
name: android-architecture-codebase
description: Step-by-step workflow and house rules for adding or changing features in this Android Kotlin codebase (MVVM + Clean Architecture, single :app module, multi-Activity XML + ViewBinding, Hilt, Retrofit + kotlinx-serialization, Room, Coroutines/Flow + LiveData, online-first data policy). Use this whenever a task touches app code in this repo — a new screen or Activity, a new API endpoint, a new Room table/DAO, a repository, use case or ViewModel, changing how data is loaded or cached, DI wiring, Gradle dependencies, or the unit tests for any of these — even when the user only says things like "thêm màn hình", "thêm tính năng", "gọi API mới", "lưu dữ liệu vào Room", "thêm bảng", "sửa ViewModel", "đổi cách cache" without mentioning architecture.
---

# Android architecture codebase workflow

This repo is a Kotlin Android codebase template. The **Posts feature** (list + detail) is the reference implementation: every rule below is already satisfied there, so when in doubt, open the Posts file for the layer you are touching and mirror it rather than inventing a new pattern.

## Sources of truth

| Source | Use it for |
|---|---|
| `CLAUDE.md` (always loaded) | Build commands, AGP 9 build gotchas, the online-first repository contract, test conventions |
| Posts feature files (see layer map) | The concrete shape of every layer |
| `gradle/libs.versions.toml` | The only place versions and plugin/library aliases are declared |
| `references/templates.md` (this skill) | Copy-ready skeletons for each layer — read it when creating new files |

If this skill and the code disagree, the code wins; fix the skill afterwards.

## Step 0 — Pin down the task before writing code

1. **Data policy.** Default is online-first (network first, Room only as fallback on failure). If the user wants something else (offline-first, cache-only, stale-while-revalidate), that is a repository-contract change: it ripples through parameters in every layer, the tests, and the "Online-first repository flow" section of `CLAUDE.md`.
2. **Schema change?** If the task adds or alters a Room table, a migration decision is needed before coding (see "Room schema changes" below). Ask the user; don't pick silently.
3. **New screen = new Activity.** Fragments are allowed only for bottom sheets (`BaseBottomSheet`) and child views inside a screen (`BaseFragment`). Dialogs are static-content `AppCompatDialog`s (`BaseDialog`), not Fragments. The project deliberately has no Navigation Component and no Compose. Don't introduce them unless the user asks. Every UI class extends its base class from `base/` (see `CLAUDE.md` "Base classes").
4. **UI consumption style.** ViewModels always expose both `uiState: StateFlow` and `uiStateLiveData`. New screens collect the StateFlow with `collectWhenStarted` (from the base class) unless the user asks for LiveData. Keep the two existing screens as they are — `PostListActivity` (StateFlow) and `PostDetailActivity` (LiveData) intentionally demonstrate both styles.

## Layer map

Package root: `app/src/main/java/com/example/codebase/`. Dependency direction: `presentation → domain ← data`, wired by `di`. `domain` must not import Android, Room, Retrofit or serialization classes.

| Artifact | Location | Naming | Reference |
|---|---|---|---|
| Domain model | `domain/model/` | `Xxx` (plain `data class`) | `Post.kt` |
| Repository interface | `domain/repository/` | `XxxRepository` | `PostRepository.kt` |
| Use case | `domain/usecase/` | `GetXxxUseCase`, single `operator fun invoke` | `GetPostsUseCase.kt` |
| Result wrapper | `domain/util/Resource.kt` | reuse, don't duplicate | — |
| DTO | `data/remote/dto/` | `XxxDto`, `@Serializable` | `PostDto.kt` |
| API interface | `data/remote/ApiService.kt` | add methods to the existing interface | — |
| Room entity | `data/local/` | `XxxEntity`, `@Entity(tableName = "xxxs")` | `PostEntity.kt` |
| DAO | `data/local/` | `XxxDao` | `PostDao.kt` |
| Mappers | `data/mapper/XxxMappers.kt` | extension funs `toEntity()`, `toDomain()` | `PostMappers.kt` |
| Repository impl | `data/repository/` | `XxxRepositoryImpl @Inject constructor` | `PostRepositoryImpl.kt` |
| DI | `di/NetworkModule`, `DatabaseModule`, `RepositoryModule` | add providers/bindings to the existing modules | — |
| UI state | `presentation/<feature>/` | `XxxUiState` data class, all fields defaulted | `PostListUiState.kt` |
| Base classes | `base/` | `BaseActivity`, `BaseFragment`, `BaseDialog`, `BaseBottomSheet`, `BaseListAdapter`, `BaseAdapter` (drag & drop), `BaseViewHolder`, `BaseDiffCallback`, `BaseViewModel`; extend, don't duplicate | — |
| Utils | `utils/` | `XxxUtils` objects (`FileUtils`, `PrefsUtils`, `PermissionUtils`, `NetworkUtils`, …); reuse before writing a helper; pure logic as `internal` functions with JVM tests; never used from `domain` | `CLAUDE.md` "Utils" |
| ViewModel | `presentation/<feature>/` | `XxxViewModel : BaseViewModel<XxxUiState>`, `@HiltViewModel` | `PostListViewModel.kt` |
| Activity | `presentation/<feature>/` | `XxxActivity : BaseActivity<ActivityXxxBinding>`, `@AndroidEntryPoint` | `PostListActivity.kt` |
| Fragment / Bottom sheet | `presentation/<feature>/` | `XxxFragment`, `XxxBottomSheet` extending the matching base, `@AndroidEntryPoint` if it injects or uses `viewModels()` | `references/templates.md` |
| Dialog | `presentation/<feature>/` | `XxxDialog : BaseDialog<DialogXxxBinding>`; data and callbacks via constructor, no Hilt, no ViewModel | `references/templates.md` |
| Adapter | `presentation/<feature>/` | `XxxAdapter : BaseListAdapter<Xxx, ItemXxxBinding>`; `BaseAdapter` only for drag & drop | `PostAdapter.kt` |
| Layout | `res/layout/` | `activity_<feature>.xml`, `fragment_<name>.xml`, `dialog_<name>.xml`, `bottom_sheet_<name>.xml`, `item_<thing>.xml`; ids in camelCase | `activity_post_list.xml` |
| Unit tests | `app/src/test/java/com/example/codebase/` mirroring the main package | `XxxTest` | `PostRepositoryImplTest.kt` |

`<feature>` is a lowercase package name without separators (`postlist`, `postdetail`).

## Workflow: new feature (vertical slice)

Build inner layers first so the project compiles after every step.

1. **Dependencies (only if genuinely needed).** Add the version under `[versions]` and the alias under `[libraries]`/`[plugins]` in `libs.versions.toml`, then reference it as `libs.xxx` in `app/build.gradle.kts`. Use `ksp(...)` for annotation processors, never `kapt`. Check the version against an official release page, or against lint's `GradleDependency` report — don't rely on memory.
2. **Domain.** Model, then add methods to the repository interface returning `Flow<Resource<T>>`, then a use case that only delegates. No default parameters that don't change behavior.
3. **Remote.** DTO with field names matching the JSON exactly (use `@SerialName` otherwise), then an `ApiService` `suspend` method. Paths are relative with no leading slash (`@GET("posts/{id}")`), because `BASE_URL` in `NetworkModule` ends with `/`. Add a new base URL or `Retrofit` instance only if the user asks.
4. **Local.** Entity, then DAO (reads return `Flow`, writes are `suspend`), then add the entity to `@Database(entities = [...])` in `AppDatabase` together with the schema-version decision, then a `provideXxxDao` in `DatabaseModule`.
5. **Mappers.** `XxxDto.toEntity()` and `XxxEntity.toDomain()`. The UI always reads from Room, so a DTO → domain mapper is normally unnecessary.
6. **Repository.** Implement the online-first template (`references/templates.md`), then add `@Binds @Singleton` for it in `RepositoryModule`.
7. **Presentation.**
   - Create the UiState, then the ViewModel with a `loadJob` that is cancelled before every new load.
   - Create the layout. The root view needs `android:id="@+id/main"` for the edge-to-edge inset listener, and `tools:context` must use the full `.presentation.<feature>.XxxActivity` path.
   - Create the Activity. Put user-visible text in `res/values/strings.xml`.
   - Register the Activity in `AndroidManifest.xml` (`android:exported="false"`, `android:windowSoftInputMode="adjustResize"`).
   - For navigation, declare `const val EXTRA_...` in the *target* Activity's `companion object` and start it with `Intent(...).putExtra(...)`.
8. **Tests.** At minimum:
   - Repository: success returns network data; network error falls back to cached data in `Error.data`; empty cache gives `data == null`; `CancellationException` propagates.
   - ViewModel: Loading → Success, error keeps previous data, reloading cancels the previous collection.
   - Use case: pass-through.
   Follow the test conventions in `CLAUDE.md`.
9. **Verify** (see "Verification" below). Report exactly what was verified and what wasn't.
10. **Record decisions.** If you changed a convention, the data policy, a build workaround or the architecture, update `CLAUDE.md` in the same change, and this skill if a step changed.

## Workflow: changing an existing feature

- **Ripple check before and after the edit.** When changing any signature (repository, use case, DAO, ViewModel method, Activity extra), grep for every call site, *including test fakes*. Test files contain hand-written `PostRepository` implementations (`FakePostsRepository`, `FakePostByIdRepository`) and a `FakePostDao` that only fail at `compileDebugUnitTestKotlin`, after the app itself builds green.
- **Remove parameters that stop doing anything.** When a policy change makes a parameter meaningless (for example `forceRefresh` after switching to online-first), delete it across all layers and tests rather than leaving a no-op.
- **Keep the repository contract intact:** `CancellationException` rethrown before the generic `catch (e: Exception)`, `emit` outside `try`, Room as the source of what's displayed, `Error` carrying fallback data.

## Data and format conventions

- **JSON:** kotlinx-serialization only (no Gson/Moshi). The shared `Json` has `ignoreUnknownKeys = true` and `coerceInputValues = true`. Make DTO fields nullable only when the API can actually omit them.
- **`Resource<T>` semantics:**
  - `Loading()` is emitted first.
  - `Success(data)` carries data read from Room after a successful write.
  - `Error(message, data)` carries the cached fallback, or `null` if nothing is cached. The ViewModel keeps the previous UI data when `data` is `null`.
- **Error messages** come only from `toErrorMessage()` in the repository file (`Network error: …`, `Server error: <code>`, `Unexpected error: …`). Extend that function instead of building messages inline.
- **Write strategy:**
  - Full-list endpoints use a `@Transaction` `clearAndInsertAll` (replace everything).
  - Single-item endpoints use `@Insert(onConflict = REPLACE)`.
  - Paginated or filtered endpoints must *not* use `clearAndInsertAll`, since it would wipe rows the request didn't return.
- **IDs and extras:** extras are primitive IDs, not whole objects. The receiving Activity reloads by ID through its ViewModel.
- **Resources:** strings go in `strings.xml` with placeholders (`User #%1$d`), never concatenated in `setText`. Prefer theme attributes or `colors.xml` over hex values. The existing hardcoded hex colors and the unused strings (`posts_title`, `error_generic`, `retry`, `loading`) are known debt — don't copy them.

## Where to update what

| Change | Also update |
|---|---|
| New dependency or plugin | `libs.versions.toml` (alias + version) and `app/build.gradle.kts`; root `build.gradle.kts` (`apply false`) for plugins |
| New entity | `AppDatabase` entities list, database version or migration, `DatabaseModule` DAO provider |
| New repository | `RepositoryModule` `@Binds` |
| New Activity | `AndroidManifest.xml`, `strings.xml` |
| New network permission or host config | `AndroidManifest.xml` (`INTERNET` and `ACCESS_NETWORK_STATE` are already declared) |
| Feature needs a runtime permission | Declare it in `AndroidManifest.xml` and request it with `PermissionUtils.request`; if it only exists on some API levels, add it to `PERMISSION_SDK_RANGES` in `PermissionUtils.kt` |
| File shared with other apps | Write it under `InternalStorageUtils.sharedDir` or `CacheUtils.sharedCacheDir`; any other location needs a `res/xml/file_paths.xml` entry |
| Build workaround flag | `gradle.properties`, with a comment citing the issue |
| Policy or convention change | `CLAUDE.md` and this skill |

## Citing sources

When pinning an unusual version, adding a workaround flag, or relying on a non-obvious library behavior, leave a one-line comment with the reason and a link to the official issue or doc (for example, the `android.disallowKotlinSourceSets=false` line in `gradle.properties` cites `google/ksp#2729`). Prefer official sources: Android Developers release notes, library GitHub releases or issues, kotlinlang.org. Search snippets and page summaries have produced wrong release dates before, so confirm a version on the release page or with lint.

## Verification

Run from PowerShell (see `CLAUDE.md` for why not `gradlew.bat` in agent shells):

```powershell
java -jar gradle\wrapper\gradle-wrapper.jar assembleDebug testDebugUnitTest --console=plain
java -jar gradle\wrapper\gradle-wrapper.jar lintDebug --console=plain
```

- **Count tests from fresh results.** Delete `app\build\test-results\testDebugUnitTest` first, then sum `tests`/`failures`/`errors` from the XML files. Old XML files survive a failed compile and report stale numbers.
- **Read lint from the SARIF report** (`app/build/reports/lint-results-debug.sarif`). The console prints nothing. Look for new `SetTextI18n`, `UnusedResources` or `HardcodedText` entries caused by your change.
- **Say what wasn't checked.** No emulator/AVD is set up on this machine. Say explicitly that UI behavior was not verified on a device instead of implying it works.

## Common mistakes to avoid

Each of these has already happened in this repo.

**Architecture**
- Putting `Resource` (or anything the repository interface returns) in `presentation`. That inverts the dependency direction; it belongs in `domain/util`.
- Letting `XxxDto` or `XxxEntity` reach a ViewModel or Activity. Map to the domain model in `data`.

**Coroutines and Flow**
- Calling `launchIn(viewModelScope)` on every load without cancelling the previous `Job`. The repository flow ends in a Room Flow that never completes, so each pull-to-refresh adds a permanent collector and the UI state flickers between collectors.
- Catching only `IOException` / `HttpException`. A `SerializationException` from malformed JSON escapes and crashes the app.
- The opposite mistake: a bare `catch (e: Exception)` without first rethrowing `CancellationException`. Every cancelled reload then turns into a fake `Error`.
- Emitting cached data before the network result. That's offline-first behavior and contradicts the current policy.

**Room schema changes**
- Changing an entity without bumping the `AppDatabase` version crashes existing installs ("Room cannot verify the data integrity").
- Bumping the version without a `Migration` crashes too ("A migration from 1 to 2 was required but not found"). No fallback is configured today.
- The cache is disposable under online-first, so `fallbackToDestructiveMigration(dropAllTables = true)` on the builder is a reasonable option. It is still a policy choice: ask the user, and record the choice in `CLAUDE.md`.

**Base classes and Fragments**
- Overriding `onCreate` / `onCreateView` for setup, or calling `enableEdgeToEdge` / setting an inset listener again in a subclass. Use the `initView` / `initListeners` / `observeData` hooks and override `applyWindowInsets` if needed.
- Keeping a Fragment binding in a plain `lateinit var` (it leaks the view after `onDestroyView`). The Fragment-based bases already null it.
- Collecting in a Fragment with `lifecycleScope` instead of `viewLifecycleOwner`. Use the base class's `collectWhenStarted`.
- Giving a `BaseDialog` layout no background. The window background is transparent.
- Putting a ViewModel, Flow collection or loading state in a `BaseDialog`. It has no lifecycle and isn't restored after rotation; load data in the screen and pass the result into the dialog's constructor.
- Setting click listeners in an adapter's `bind` or capturing `position` in them. Set them in `onViewHolderCreated` and use `getItemOrNull(holder)`.
- Using `BaseListAdapter` for a draggable list, or calling `submitList` from `ItemTouchHelper.onMove`. The async diff lags behind the drag and moves the wrong rows; extend `BaseAdapter` and use `moveItem`.
- Using `BaseAdapter` for a list that is never dragged. Default to `BaseListAdapter`.
- Not reporting the new order to the ViewModel after a drag. The next `uiState` emission snaps the list back.

**Android wiring**
- Forgetting to register a new Activity in the manifest. It compiles, then throws `ActivityNotFoundException` at runtime.
- Hardcoding UI strings or concatenating them in `setText` (lint `SetTextI18n`).
- Adding string resources "for later" and never using them (lint `UnusedResources`).

**Build (AGP 9.3)**
- Applying `org.jetbrains.kotlin.android` or writing `kotlinOptions {}` — AGP 9 compiles Kotlin itself.
- Using Hilt older than 2.59, which fails with "Android BaseExtension not found".
- Removing `android.disallowKotlinSourceSets=false`, which breaks KSP.
- Leaving dead aliases in `libs.versions.toml` after removing a plugin. The unused `kotlin-android` alias is still there.

**Tests**
- Two `private` top-level fake classes with the same name in one package — this is a JVM class-name clash, so the compile fails.
- Constructing a ViewModel without `InstantTaskExecutorRule`. The eager `asLiveData()` hits `Looper.getMainLooper()`.
- Asserting `uiStateLiveData.value` without `observeForever {}`. It stays `null`.
- Using `yield()` to separate fake emissions. `runCurrent()` drains it, so intermediate states can't be observed; use `delay(1)`.
- Using Turbine's `awaitComplete()` on a successful repository flow. It never completes; use `cancelAndIgnoreRemainingEvents()`.

**Tooling on this Windows host**
- Running `gradlew.bat` from agent shells, `cmd.exe /c` from Git Bash (it opens an interactive prompt and runs nothing), passing `-D...` args in PowerShell 5.1, or putting `*> file` after PowerShell's `--%` token (the redirection is passed to Gradle as a task name).

**Reporting**
- Claiming the feature works on the strength of "build green" alone. Report which of build, unit tests and lint passed, and that UI behavior was not verified on a device.
