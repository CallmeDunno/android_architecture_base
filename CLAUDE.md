# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android boilerplate in Kotlin: MVVM + Clean Architecture, single Gradle module (`:app`), XML layouts + ViewBinding, **multi-Activity** navigation (no Navigation Component, no Fragments, no Compose). Stack: Hilt, Retrofit 3 + OkHttp 4 + kotlinx-serialization, Room 2.x, Coroutines/Flow, LiveData. The sample feature is a Posts list/detail backed by `https://jsonplaceholder.typicode.com/`. All dependency versions live in `gradle/libs.versions.toml`.

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

Each screen is an `@AndroidEntryPoint` Activity + `@HiltViewModel` + a `*UiState` data class. ViewModels keep a private `MutableStateFlow` and expose both `uiState: StateFlow` and `uiStateLiveData = uiState.asLiveData()`. By design `PostListActivity` (launcher) collects the StateFlow with `repeatOnLifecycle(STARTED)`, while `PostDetailActivity` observes the LiveData — both consumption styles are intentionally demonstrated. Screens are opened via `Intent` extras (`PostDetailActivity.EXTRA_POST_ID`); the detail Activity calls `viewModel.loadPost(id)` itself rather than using `SavedStateHandle`, skipping the call if the ViewModel already has data or is loading.

## Unit test conventions

- ViewModel tests need both `InstantTaskExecutorRule` (the eager `asLiveData()` in the ViewModel constructor touches the main looper, even if the test ignores LiveData) and `MainDispatcherRule` (`app/src/test/.../util`).
- Pass the rule's dispatcher to `runTest(mainDispatcherRule.testDispatcher)` so `viewModelScope` shares the test scheduler. To assert intermediate states, separate emissions in fake flows with `delay(...)` and step with `runCurrent()` / `advanceUntilIdle()` — `yield()` is not enough, `runCurrent()` drains zero-delay tasks.
- `asLiveData()` only collects with an active observer: call `observeForever {}` before asserting `uiStateLiveData.value`.
- Repository tests use an in-memory `FakePostDao` backed by `MutableStateFlow` plus mockk for `ApiService` (no Robolectric). Because successful flows never complete, use `cancelAndIgnoreRemainingEvents()` in Turbine, not `awaitComplete()`.
- Top-level `private` helper classes in different test files of the same package still clash at JVM level — give fakes distinct names.
