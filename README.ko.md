# SC:R 모드

[English](README.md) | **한국어**

스타크래프트: 리마스터 1.23.10을 SC4J 모드로 다시 구현한 것입니다. 모드 id는 `scr`, jar는 `scr.jar`입니다. 게임 데이터는 실행할 때 플레이어 본인의 스타크래프트: 리마스터 설치본에서 읽으며, 저장소와 jar에는 들어 있지 않습니다.

[SC4J 엔진](https://github.com/fogofwarlabs/sc4j/blob/main/README.ko.md) 위에서 자체 애플리케이션으로 실행되어 프레임이 원본 게임을 따릅니다([모드 제작 가이드 20장](https://github.com/fogofwarlabs/sc4j/blob/main/docs/mod-guide.ko.md#20-모드가-실행되는-두-방식)).

- [구성](#구성)
- [게임 데이터](#게임-데이터)
- [빌드와 실행](#빌드와-실행)
- [테스트](#테스트)
- [프레임 기록기](#프레임-기록기)
- [런처 그림](#런처-그림)
- [기여](#기여)
- [라이선스](#라이선스)

## 구성

| 경로 | 내용 |
|---|---|
| `scr-data` | 설치본의 데이터 파일과 콘텐츠 표, 추가 유닛을 위한 확장 API |
| `scr-game` | 게임: 유닛, 명령, 전투, 컴퓨터 플레이어, 트리거, 리플레이와 저장 게임 |
| `scr-render` | 게임 화면 렌더러 |
| `scr-net` | LAN과 Beacon.Link 플레이 |
| `scr-client` | 클라이언트 애플리케이션: 메뉴, 게임 화면, 오디오, 리플레이 재생 |
| `scr-benchmark` | JMH 벤치마크. `scr.jar`에 들어가지 않음 |
| `sc-recorder` | 원본 게임의 프레임 기록기([아래](#프레임-기록기)). `scr.jar`에 들어가지 않음 |
| `mod.properties` | 클래스와 리소스가 `scr.jar`를 이루는 모듈과 그 컴파일러 인자 |
| `build-logic/` | SC:R 빌드의 Gradle 규칙 |
| `docs/` | SCRECBIN 형식과 런처 그림 |
| `licenses/` | 서드파티 라이브러리의 라이선스 전문 |

## 게임 데이터

Gradle 속성 `sc4j.data`는 스타크래프트: 리마스터 설치 폴더(`.build.info`와 `x86_64/StarCraft.exe`가 있는 폴더)를 가리킵니다. 다음 세 방법 중 하나로 설정합니다.

| 위치 | 방법 |
|---|---|
| Gradle 사용자 속성 | `%USERPROFILE%\.gradle\gradle.properties`(`GRADLE_USER_HOME`)에 `sc4j.data=<설치 폴더>` 한 줄 |
| 명령줄 | `-Psc4j.data=<설치 폴더>` |
| 환경 변수(CI) | `ORG_GRADLE_PROJECT_sc4j.data=<설치 폴더>` |

`.properties` 파일에서는 Windows 경로의 역슬래시를 두 번 쓰거나 슬래시를 씁니다. `sc4j.data`가 설정되어 있으면 `installMod`는 설치본에 공유 게임 폴더 설정이 아직 없을 때 이 값을 써 넣습니다.

런처는 Windows에 기록된 위치에서 스타크래프트: 리마스터 폴더를 찾고, 찾지 못하면 모드 설정에서 한 번 지정하면 됩니다.

게임 데이터는 절대 저장소에 넣지 않습니다. 설치본의 파일도, 그 파일에서 뽑아내거나 변환한 것(그림, 소리, 데이터 파일, 맵)도 안 됩니다. SC:R 모드와 테스트는 실행할 때 설치본에서 직접 읽습니다.

## 빌드와 실행

JDK 25가 필요합니다. 플레이하려면 스타크래프트: 리마스터 1.23.10 설치본이 필요하지만, 빌드에는 필요하지 않습니다. 아래 명령은 저장소 루트의 PowerShell에서 실행합니다. Linux에서는 `./gradlew`를 씁니다.

```powershell
git clone https://github.com/fogofwarlabs/sc4j-scr.git
cd sc4j-scr
.\gradlew.bat build
```

빌드는 엔진 버전 `sc4jVersion`(`gradle.properties`)의 엔진 모듈과 모드 플러그인을 Maven Central이나 그 스냅샷에서 가져옵니다.

엔진 체크아웃 [sc4j](https://github.com/fogofwarlabs/sc4j)가 이 체크아웃 옆(`../sc4j`)에 있으면 빌드는 그 체크아웃을 대신 씁니다. `-Psc4j.engine.dir=<폴더>`로 다른 엔진 체크아웃을 지정하고, `-Psc4j.engine.local=false`로 끕니다.

| 명령 | 하는 일 |
|---|---|
| `.\gradlew.bat jar` | `build/libs/scr.jar` 빌드 |
| `.\gradlew.bat installMod` | `scr.jar`를 설치본의 `mods/`에 복사. 엔진 체크아웃이 있으면 그 개발용 설치본 `../sc4j/build/dev-install/`, 없으면 설치본 `-Psc4j.install=<폴더>` |
| `.\gradlew.bat :run` | `scr.jar`를 그 설치본의 사용자 데이터로 엔진 런타임에서 실행(설치본이 없으면 `build/sc4j-run/`) |
| `..\sc4j\gradlew.bat -p ..\sc4j devInstall` | 엔진 체크아웃의 개발용 설치본 만들기. `installMod` 전에 실행 |
| `..\sc4j\gradlew.bat -p ..\sc4j runDevLauncher` | 그 개발용 설치본의 런처 열기 |
| `..\sc4j\gradlew.bat -p ..\sc4j runDevInstall -Psc4j.mod=scr` | 그 개발용 설치본의 런타임을 `mods/scr.jar`로 실행 |

`installMod`는 설치본이 없으면 실패합니다. 앞의 콜론 없이 `run`을 쓰면 `scr-client`와 `sc-recorder`의 애플리케이션도 시작합니다.

## 테스트

| 명령 | 실행 내용 | 필요한 것 |
|---|---|---|
| `.\gradlew.bat build` | 컴파일, 단위 테스트(`test`), 검사 | 저장소 밖의 것 없음 |
| `.\gradlew.bat integrationTest` | `integration` 태그 테스트(`@RequiresInstallation`), GPU 렌더 스모크 테스트 포함 | `sc4j.data`, 스모크 테스트에는 GPU |
| `.\gradlew.bat corpusTest` | `corpus` 태그 테스트(`@CorpusTest`): 오래 걸리는 실행과 외부 리플레이, 맵 폴더 테스트 | `sc4j.data`, 폴더 속성이 없는 테스트는 건너뜀 |

`integrationTest`는 `sc4j.data`가 없거나 폴더가 아니면 시작하기 전에 실패합니다. GPU 렌더 스모크 테스트는 Gradle 속성 `sc4j.render.smoke`가 `true`일 때 실행되며(`gradle.properties`의 기본값), `-Psc4j.render.smoke=false`로 끕니다.

코퍼스 단계의 외부 폴더도 Gradle 속성입니다.

| 속성 | 폴더 |
|---|---|
| `sc4j.replays` | 리플레이 코퍼스의 루트 |
| `sc4j.replays.limit` | 리플레이 파싱 테스트가 읽는 파일 수 |
| `sc4j.maps` | 큰 EUD 맵이 있는 맵 폴더 |
| `sc4j.corpus` | 코퍼스 조사가 재생하는 리플레이 폴더(구조 자유) |

## 프레임 기록기

`sc-recorder`는 원본 게임의 프레임을 읽기만 하는 기록기로, SC:R 모드를 원본 게임과 비교하는 데 씁니다. 게임의 관찰된 상태를 프레임마다 SCRECBIN 파일에 기록합니다: [SCRECBIN 형식](docs/sc-recorder-format.ko.md).

## 런처 그림

SC:R 모드의 런처 아이콘과 배너, 넓은 배너, 그리고 내보내기 방법: [`docs/branding/`](docs/branding/README.ko.md).

## 기여

개발 환경, 코드 스타일, 커밋과 풀 리퀘스트는 엔진의 [기여 안내](https://github.com/fogofwarlabs/sc4j/blob/main/CONTRIBUTING.ko.md)를 따릅니다. SC:R 모드를 건드렸고 설치본이 있으면 `integrationTest`도 실행합니다.

## 라이선스

SC:R 모드는 [Apache License 2.0](LICENSE)으로 배포됩니다. 저장소와 배포본에 들어 있는 서드파티 자료는 [NOTICE](NOTICE)에 있고, 라이선스 전문은 [`licenses/`](licenses/)에 있습니다.

StarCraft는 Blizzard Entertainment, Inc.의 상표입니다. SC4J는 Blizzard Entertainment와 제휴하거나 승인받지 않았습니다. SC:R 모드는 플레이어 본인의 스타크래프트: 리마스터 설치본에서 게임 데이터를 읽으며, 저장소와 배포본에는 그런 데이터가 들어 있지 않습니다.
