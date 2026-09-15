# 확장 학습. CI와 배포 승인, 백업, 인증서 갱신, 장애 알림

앞 장의 자동화 테스트·Flyway·HTTPS·요청 로그 다음에 이어지는 원고입니다. VS Code에서 코드를 작성하고 GitHub 웹 화면에서 CI와 승인을 확인합니다. 운영 명령과 서비스 파일은 Linux 서버에서 사용합니다.

## 1. 장의 학습 목표

앞 장에서는 변경한 코드가 기존 기능을 유지하는지 검사했습니다. 이번 장에서는 그 검사를 GitHub에 연결하고, 승인한 실행 파일을 배포하며, 배포 후에도 서비스 상태를 확인합니다.

1. Push와 Pull Request에서 테스트와 Jar 빌드를 자동 실행할 수 있습니다.
2. 검증한 Jar를 다시 빌드하지 않고 승인 후 배포할 수 있습니다.
3. DB 백업과 복구 검증을 배포 확인 항목에 포함할 수 있습니다.
4. 인증서를 자동 갱신하고 갱신 결과를 확인할 수 있습니다.
5. 장애·복구·인증서 만료 임박을 외부 감시 서버에서 알릴 수 있습니다.

## 2. 핵심 개념과 실습 환경

### 앞 장과의 연결

기존 게시판, DB 로그인, 검색·페이징, REST API, Firebase 관리자 화면, WebJars, Profile을 유지합니다. 앞 장의 테스트와 Flyway 설정을 먼저 적용해야 합니다. 이 장의 파일을 추가하는 것만으로 아직 작성하지 않은 테스트나 운영 DB가 생성되지는 않습니다.

| 구분 | 이 장의 기준 |
| --- | --- |
| 소스 프로젝트 | 저장소 안의 `chapter13/spring` |
| 개발 | Windows + VS Code |
| CI | GitHub-hosted Ubuntu runner, Java 21 |
| 앱 서버 | Linux + systemd + Java 21 이상 + Nginx |
| 데이터베이스 | 앞 장에서 준비한 Oracle |
| 공개 주소 | `board.example.com`을 자신의 도메인으로 교체 |
| 모니터링 | 앱 서버와 다른 Linux 호스트 |

이 장에서는 **Nginx가 443 HTTPS를 처리하고, Spring Boot는 127.0.0.1:8080에서 실행**합니다. 앞 장의 내장 서버 TLS 실습인 `prod,https`와 구분하여 `prod,proxy` Profile을 사용합니다. Firebase Admin SDK의 자격 증명과 외부 호출은 계속 Spring Boot 서버가 담당합니다.

기본 브랜치는 예시로 `main`을 사용합니다. 실제 저장소가 다른 이름이라면 YAML의 브랜치 조건도 함께 바꿉니다. 서버 설치와 DNS·방화벽·Oracle 관리 권한은 별도로 준비합니다.

### 자동화할 것과 승인할 것

CI는 코드와 테스트를 검사합니다. 운영 승인은 변경 내용을 운영에 적용해도 되는지 사람이 판단하는 단계입니다. DB 백업 성공만으로 복구 가능성이 증명되지는 않으며, 이전 Jar로 돌려도 DB 변경이 자동으로 되돌아가지는 않습니다.

## 3. 단계별 예제

### 확장 05. GitHub Actions에서 검증하고 승인받기

#### 05-1. 테스트가 독립적으로 실행되는지 확인하기

앞 장의 `ApplicationRegressionTests`와 H2 테스트 의존성을 먼저 적용합니다. 기존 `ApplicationTests` 등 다른 테스트도 운영 DB나 Firebase에 접근하지 않도록 점검합니다.

CI에는 실제 DB 비밀번호와 Firebase 키를 전달하지 않습니다. CI의 H2 성공과 실제 Oracle 마이그레이션 성공은 구분합니다. Oracle 검증은 별도 검증 DB에서 수행하고 배포 승인 시 결과를 확인합니다.

#### 05-2. 저장소 루트에 워크플로 추가하기

파일 위치는 `chapter13/spring/.github`가 아니라 **저장소 루트의 `.github/workflows/ci-release.yml`**입니다.

```yaml
name: Verify and Release

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: verify-release-${{ github.ref }}
  cancel-in-progress: false

jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run:
        working-directory: chapter13/spring
    env:
      SPRING_PROFILES_ACTIVE: test
      SPRING_DATASOURCE_URL: jdbc:h2:mem:ci;DB_CLOSE_DELAY=-1
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: org.h2.Driver
      SPRING_DATASOURCE_USERNAME: sa
      SPRING_DATASOURCE_PASSWORD: ""
      SPRING_JPA_DATABASE_PLATFORM: org.hibernate.dialect.H2Dialect
      SPRING_JPA_HIBERNATE_DDL_AUTO: create-drop
      SPRING_SQL_INIT_MODE: never
      SPRING_FLYWAY_ENABLED: "false"
      APP_FIREBASE_ENABLED: "false"
      FIREBASE_ENABLED: "false"
      SERVER_SSL_ENABLED: "false"
    steps:
      - uses: actions/checkout@v6
        with:
          persist-credentials: false
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - name: Verify and package
        run: |
          test -f src/test/java/com/study/spring/ApplicationRegressionTests.java
          bash ./gradlew --no-daemon clean test bootJar
          cd build/libs
          sha256sum spring-boot-study.jar > spring-boot-study.jar.sha256
      - name: Save test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            chapter13/spring/build/reports/tests/
            chapter13/spring/build/test-results/
          if-no-files-found: warn
          retention-days: 7
      - name: Save verified package
        uses: actions/upload-artifact@v4
        with:
          name: verified-package
          path: |
            chapter13/spring/build/libs/spring-boot-study.jar
            chapter13/spring/build/libs/spring-boot-study.jar.sha256
          if-no-files-found: error
          retention-days: 7

  deploy:
    if: github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main'
    needs: verify
    runs-on: ubuntu-latest
    timeout-minutes: 15
    environment: production
    concurrency:
      group: production-deployment
      cancel-in-progress: false
    env:
      DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
      DEPLOY_USER: ${{ secrets.DEPLOY_USER }}
      DEPLOY_KEY: ${{ secrets.DEPLOY_KEY }}
      KNOWN_HOSTS: ${{ secrets.DEPLOY_KNOWN_HOSTS }}
    steps:
      - uses: actions/download-artifact@v4
        with:
          name: verified-package
          path: package
      - name: Prepare SSH
        shell: bash
        run: |
          umask 077
          mkdir -p ~/.ssh
          printf '%s\n' "$DEPLOY_KEY" > ~/.ssh/id_ed25519
          printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/known_hosts
          test -n "$DEPLOY_HOST"
          test -n "$DEPLOY_USER"
      - name: Upload and deploy the verified package
        shell: bash
        run: |
          set -euo pipefail
          release="${GITHUB_SHA}-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
          [[ "$release" =~ ^[0-9a-f]{40}-[0-9]+-[0-9]+$ ]]
          target="${DEPLOY_USER}@${DEPLOY_HOST}"
          ssh -o BatchMode=yes -o StrictHostKeyChecking=yes "$target" \
            "mkdir -p /opt/spring-board/incoming/$release"
          scp -o BatchMode=yes -o StrictHostKeyChecking=yes \
            package/spring-boot-study.jar package/spring-boot-study.jar.sha256 \
            "$target:/opt/spring-board/incoming/$release/"
          ssh -o BatchMode=yes -o StrictHostKeyChecking=yes "$target" \
            "bash /opt/spring-board/bin/deploy.sh $release"
```

1. Push와 PR에서는 검증만 실행합니다.
2. GitHub의 수동 실행은 선택한 커밋을 다시 검증한 뒤 배포 승인 단계로 넘어갑니다.
3. 배포는 같은 실행에서 통과한 Jar를 내려받습니다. 서버에서 다시 빌드하지 않습니다.
4. `needs: verify` 때문에 검증이 실패하면 배포하지 않습니다.
5. SHA-256은 파일 전송 무결성 확인용입니다. 독립된 서명이나 공급망 인증을 대신하지 않습니다.
6. 프로젝트 경로와 Jar 이름을 바꾸었다면 관련 항목을 모두 맞춥니다.

이 예제는 GitHub 공식 Actions의 메이저 태그를 사용합니다. 조직 운영 정책에 맞춰 검토한 전체 commit SHA로 고정하고 업데이트를 관리할 수 있습니다. [GitHub Gradle CI 안내](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle)

#### 05-3. GitHub에서 승인 환경 구성하기

1. 저장소의 **Settings → Environments**에서 `production`을 만듭니다.
2. Required reviewers를 설정하고, 가능하면 자기 승인과 관리자 우회를 제한합니다.
3. 배포 허용 브랜치를 `main`으로 제한합니다.
4. 다음 Secrets를 `production` 환경에 등록합니다.

| Secret | 내용 |
| --- | --- |
| DEPLOY_HOST | 배포 서버 주소 |
| DEPLOY_USER | 전용 SSH 배포 사용자, 예: `springdeploy` |
| DEPLOY_KEY | 전용 SSH 개인 키 |
| DEPLOY_KNOWN_HOSTS | 관리자가 지문을 확인한 서버 호스트 키 항목 |

호스트 키는 서버 관리 경로에서 진위를 확인한 후 등록합니다. 배포 중 처음 조회한 키를 무조건 신뢰하거나 `StrictHostKeyChecking=no`를 사용하지 않습니다.

**`environment: production` 한 줄만으로 승인 대기가 생기지는 않습니다.** GitHub 설정에서 reviewer 보호 규칙이 실제 활성화되어 있어야 합니다. 저장소 공개 여부와 요금제에 따라 지원 범위가 다릅니다. 기능을 사용할 수 없다면 이 예제의 승인 자동화가 완성되었다고 간주하지 않습니다. [GitHub 배포 환경과 보호 규칙](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)

브랜치 규칙에도 `verify` 검사를 필수로 등록하고, 워크플로·배포 스크립트 변경은 리뷰를 받도록 구성합니다.

### 확장 06. 승인한 Jar를 서버에 배포하기

#### 06-1. 건강 상태와 배포 버전 표시하기

기존 `build.gradle`에 추가합니다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

새 `application-proxy.properties`를 만듭니다.

```properties
server.address=127.0.0.1
server.port=8080
server.ssl.enabled=false
server.forward-headers-strategy=framework
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax

management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
management.endpoint.health.show-components=never
app.release-id=${APP_RELEASE_ID:local}
```

기존 `SecurityConfig`의 `anyRequest()`보다 앞에 **GET `/actuator/health`만** 허용합니다.

```java
.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
```

Firebase 전용 필터 체인은 유지합니다. Actuator 전체를 공개하지 않습니다. 기본 건강 상태에 포함되는 DB 연결은 검사할 수 있지만 모든 기능과 Firebase 권한까지 검증하는 것은 아닙니다. [Spring Boot Actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)

앞 장의 `RequestLogFilter`에 다음 필드를 추가합니다.

```java
@org.springframework.beans.factory.annotation.Value("${app.release-id:local}")
private String releaseId;
```

`X-Request-ID`를 응답에 설정하는 코드 바로 다음 줄에 추가합니다.

```java
response.setHeader("X-App-Release", releaseId);
```

배포 스크립트가 응답 헤더에서 실행 중인 버전을 확인합니다. 버전 ID는 비밀값이 아닌 Git commit과 Actions 실행 번호입니다.

#### 06-2. 운영 서버의 디렉터리와 계정 준비하기

관리자는 다음 경로와 두 전용 계정을 준비합니다.

```text
/opt/spring-board/incoming/        업로드 대기 파일
/opt/spring-board/releases/        배포 버전별 Jar
/opt/spring-board/bin/deploy.sh    배포 스크립트
/opt/spring-board/current.jar      현재 Jar 심볼릭 링크
/opt/spring-board/release.env      현재 배포 ID
/etc/spring-board/app.env          DB·Firebase 등 운영 비밀 설정
/var/log/spring-board/             앱 로그
```

`springdeploy`는 incoming·releases·current.jar·release.env를 관리합니다. `springboard`는 앱 실행 계정으로 Jar 읽기와 로그 쓰기 권한을 가집니다. 앱 계정은 배포 스크립트·Jar를 수정하지 못하게 합니다. `app.env`와 Firebase 키는 실행에 필요한 계정만 접근하도록 제한합니다.

`/etc/spring-board/app.env` 예시입니다. 실제 값으로 바꾸되 Git에 올리지 않습니다.

```text
SPRING_PROFILES_ACTIVE=prod,proxy
DB_URL=실제_운영_JDBC_URL
DB_USERNAME=실제_운영_DB_사용자
DB_PASSWORD=실제_운영_DB_비밀번호
FLYWAY_ENABLED=true
FIREBASE_ENABLED=false
APP_LOG_FILE=/var/log/spring-board/application.log
```

이 파일은 systemd의 EnvironmentFile 형식입니다. `export`를 붙이지 않으며 공백 등이 있는 값은 해당 문법에 맞게 따옴표 처리합니다. Flyway는 앞 장의 기존 DB Baseline 도입을 완료한 후에 켭니다. Firebase를 사용하려면 `true`와 프로젝트 ID·키 경로를 추가합니다.

#### 06-3. systemd 서비스 등록하기

`/etc/systemd/system/spring-board.service`

```ini
[Unit]
Description=Spring Board
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=springboard
Group=springboard
WorkingDirectory=/opt/spring-board
EnvironmentFile=/etc/spring-board/app.env
EnvironmentFile=/opt/spring-board/release.env
ExecStart=/usr/bin/java -jar /opt/spring-board/current.jar
Restart=on-failure
RestartSec=5
TimeoutStopSec=60
UMask=0027
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

`/usr/bin/java`는 Java 21 이상이어야 합니다. 관리자는 `systemctl daemon-reload`를 실행합니다. 첫 배포 전에는 current.jar와 release.env가 없으므로 서비스를 시작하지 않습니다. 첫 배포 성공 후 부팅 시 자동 실행을 활성화합니다.

배포 계정의 sudo 권한은 이 서비스 재시작에 필요한 명령으로 제한합니다. 예를 들어 실제 systemctl 경로가 `/usr/bin/systemctl`이면 관리자가 `visudo`로 다음 규칙을 구성합니다.

```text
springdeploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart spring-board.service
```

이 예제는 단일 인스턴스 재시작 방식이라 짧은 중단이 발생합니다. 무중단 배포가 필요한 환경에는 다중 인스턴스와 트래픽 전환을 별도로 구성합니다.

#### 06-4. 서버 배포 스크립트

`/opt/spring-board/bin/deploy.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

release="${1:?release id required}"
[[ "$release" =~ ^[0-9a-f]{40}-[0-9]+-[0-9]+$ ]] || exit 2
base=/opt/spring-board
incoming="$base/incoming/$release"
destination="$base/releases/$release"

exec 9>"$base/deploy.lock"
flock -n 9 || { echo 'Another deployment is running'; exit 3; }
test -d "$incoming"
test ! -e "$destination"
cd "$incoming"
sha256sum --check spring-boot-study.jar.sha256

mkdir "$destination"
install -m 0644 spring-boot-study.jar "$destination/spring-boot-study.jar"
previous=""
if test -f "$base/current.jar"; then
    previous="$(readlink -f "$base/current.jar")"
fi
printf '%s\n' "$previous" > "$destination/previous-jar.txt"

printf 'APP_RELEASE_ID=%s\n' "$release" > "$base/release.env.next"
chmod 0644 "$base/release.env.next"
mv -f "$base/release.env.next" "$base/release.env"
ln -s "$destination/spring-boot-study.jar" "$base/current.jar.next"
mv -Tf "$base/current.jar.next" "$base/current.jar"
sudo -n /usr/bin/systemctl restart spring-board.service

for attempt in $(seq 1 30); do
    if curl --fail --silent --show-error --max-time 4 \
            -D "$destination/health.headers" \
            http://127.0.0.1:8080/actuator/health \
            -o "$destination/health.json"; then
        if tr -d '\r' < "$destination/health.headers" | grep -qix "X-App-Release: $release" \
            && python3 -c 'import json,sys; sys.exit(json.load(open(sys.argv[1])).get("status") != "UP")' \
                "$destination/health.json"; then
            printf 'Deployment healthy: %s\n' "$release"
            exit 0
        fi
    fi
    sleep 4
done

echo 'Deployment health check failed; inspect service and migration logs.' >&2
exit 1
```

이 스크립트에는 Bash·curl·Python 3·flock이 필요합니다. 정상 응답뿐 아니라 새 배포 ID가 일치해야 성공합니다. previous-jar.txt에는 직전 Jar 경로를 남깁니다.

실패해도 DB 변경은 자동 롤백하지 않습니다. 이전 앱이 새 스키마와 호환되는지 확인한 뒤 운영자가 이전 Jar·배포 ID를 복원하고 재시작합니다. 컬럼 삭제나 타입 변경을 포함한 배포는 별도의 호환성·복구 계획 없이 진행하지 않습니다.

#### 06-5. GitHub 웹에서 배포 승인하기

1. 먼저 PR의 verify 결과와 검증 DB의 마이그레이션 결과를 확인합니다.
2. 변경을 main에 반영합니다.
3. **Actions → Verify and Release → Run workflow**에서 main을 선택합니다.
4. verify가 끝나면 보호 규칙에 따라 production 승인을 기다립니다.
5. 승인자는 대상 commit, 테스트 보고서, DB 백업·복구 검증 기록과 변경 계획을 확인합니다.
6. 승인 후 업로드·재시작·상태 확인이 실행됩니다.
7. 성공 후에도 실제 도메인에서 게시판·관리자 화면을 확인합니다.

대기 중인 오래된 실행을 승인하면 최신 배포보다 이전 commit을 배포할 수도 있습니다. 버전을 확인하고 불필요한 대기 실행을 취소합니다. 건강 상태만으로 UI·Firebase 전체 기능이 정상이라고 판단하지 않습니다.

### 확장 07. Oracle 백업과 복구 검증

#### 07-1. 백업 범위 정하기

이 실습은 Oracle Data Pump로 게시판 스키마를 내보내는 **논리 백업**입니다. 전체 DB의 장애 복구나 특정 시점 복구가 필요하면 DBA와 RMAN·아카이브 로그 백업을 별도로 구성합니다.

Data Pump dump는 명령을 실행한 PC가 아니라 Oracle의 DIRECTORY 객체가 가리키는 **DB 서버 경로**에 생성됩니다. 앱 서버의 Jar 백업과 혼동하지 않습니다.

DBA가 준비할 항목은 다음과 같습니다.

1. DB 서버의 백업 저장 디렉터리와 Oracle DIRECTORY 객체 `BOARD_BACKUP_DIR`.
2. 필요한 스키마를 내보낼 수 있는 백업 계정과 해당 DIRECTORY 권한.
3. 비밀번호를 명령에 적지 않는 Oracle Wallet 접속 별칭 `BOARD_BACKUP`.
4. 외부 저장소 복사, 접근 제한, 암호화, 보존 기간.

아래 `STUDY`는 예시 스키마 이름입니다. 실제 스키마와 일치하도록 바꿉니다.

#### 07-2. Data Pump 파라미터 파일

백업 실행 호스트의 `/etc/spring-board/board-export.par`:

```text
DIRECTORY=BOARD_BACKUP_DIR
SCHEMAS=STUDY
FLASHBACK_TIME=SYSTIMESTAMP
```

같은 시점 기준으로 내보내도록 설정합니다. 필요한 Flashback 권한과 UNDO 보존·공간은 DBA가 검토해야 합니다.

백업 실행 예시:

```bash
stamp=$(date -u +%Y%m%dT%H%M%SZ)
expdp /@BOARD_BACKUP parfile=/etc/spring-board/board-export.par \
    dumpfile="board_${stamp}_%U.dmp" logfile="board_${stamp}.log"
```

명령이 완료되었다고 바로 성공 처리하지 않습니다. 종료 코드와 Data Pump 로그의 오류를 확인하고, DB 서버의 dump 파일 존재·크기·해시를 확인합니다. 그 후 별도 저장소로 복사하고 복사본 해시도 비교합니다.

#### 07-3. 배포 승인과 연결하기

승인 기록에는 다음을 남깁니다.

| 항목 | 기록 예 |
| --- | --- |
| 대상 | DB 서비스명과 스키마, 비밀번호 제외 |
| 백업 시각 | UTC 시각 |
| 백업 파일 | dump·log 파일 식별자와 해시 |
| 외부 복사 | 저장 위치와 복사 확인 결과 |
| 복구 검증 | 최근 복구 테스트 날짜와 결과 |
| 마이그레이션 | 적용할 Flyway 버전과 호환성 검토 |

일일 자동 백업과 별도로, 스키마 변경 배포 전 필요한 백업을 수행합니다. 자동 백업은 서버 스케줄러에 등록하되 실행 주기와 허용 데이터 손실 시간에 맞춰 정합니다.

#### 07-4. 별도 스키마에 복구하기

복구 검증은 운영 `STUDY`에 덮어쓰지 않고, 비어 있는 별도 `STUDY_RESTORE` 스키마에서 진행합니다. DBA가 계정·테이블스페이스·권한을 먼저 준비합니다.

```text
impdp /@BOARD_BACKUP DIRECTORY=BOARD_BACKUP_DIR DUMPFILE=실제_dump파일명.dmp LOGFILE=restore-check.log REMAP_SCHEMA=STUDY:STUDY_RESTORE
```

dump가 여러 파일로 나뉘었다면 실제 dump 파일 집합을 지정합니다. 테이블스페이스가 다른 환경이라면 DBA가 REMAP_TABLESPACE도 검토합니다.

복구 후 게시글·회원 수, 최근 게시글 내용, 제약 조건과 Flyway 이력을 확인합니다. 검증용 앱을 복구 DB로 연결하여 로그인·CRUD를 확인하고 실제 Firebase 전송은 끕니다. 복구 시간도 기록합니다.

### 확장 08. 인증서 자동 갱신

#### 08-1. 첫 인증서 발급 준비

운영 도메인의 DNS가 Nginx 서버를 가리키고 80·443 포트로 접근할 수 있어야 합니다. 이 예제는 HTTP-01 webroot 방식입니다. 해당 방식이 불가능한 환경은 DNS-01 등 별도의 검증 방식을 선택합니다.

Nginx와 Certbot은 운영체제의 공식 설치 절차에 따라 준비합니다. `/var/www/letsencrypt`를 만들고 최초에는 다음 HTTP 서버 블록만 적용합니다.

```nginx
server {
    listen 80;
    server_name board.example.com;
    location /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
    }
    location / {
        return 301 https://board.example.com$request_uri;
    }
}
```

실제 도메인으로 바꾸고 `nginx -t` 검사 후 적용합니다. 아직 존재하지 않는 인증서 경로를 사용하는 443 블록은 발급 이후 추가합니다.

```text
sudo certbot certonly --webroot -w /var/www/letsencrypt -d board.example.com
```

#### 08-2. HTTPS 프록시 연결하기

발급 후 다음 443 서버 블록을 추가합니다.

```nginx
server {
    listen 443 ssl;
    server_name board.example.com;
    ssl_certificate /etc/letsencrypt/live/board.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/board.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host board.example.com;
        proxy_set_header X-Forwarded-Host board.example.com;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header Forwarded "";
    }
}
```

외부에서 보낸 전달 헤더를 그대로 신뢰하지 않도록 프록시가 값을 설정합니다. Spring Boot는 loopback에만 바인딩하고 외부에서 8080으로 우회 접속하지 못하게 합니다. 이는 같은 서버에 신뢰할 수 있는 Nginx가 있는 구성입니다. [Nginx reverse proxy](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/)

`nginx -t` 검사 후 reload하고 `https://실제도메인/login`으로 접속합니다. 기존 Spring Boot의 `https` Profile은 켜지 않습니다. 로그인·로그아웃, CSRF 폼, Firebase 동일 출처 요청과 WebJars 로딩을 확인합니다.

Nginx 기본 access log에는 쿼리 문자열이나 UID 경로가 들어갈 수 있습니다. 앞 장의 앱 로그만 제한했다고 모든 로그가 안전해지는 것은 아닙니다. 운영 개인정보 정책에 맞춰 프록시 로그도 별도로 설정합니다.

#### 08-3. 갱신 후 인증서 다시 읽기

root 소유의 `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh`를 만들고 실행 권한을 설정합니다.

```sh
#!/bin/sh
set -eu
/usr/sbin/nginx -t
/usr/bin/systemctl reload nginx
```

실제 nginx·systemctl 경로를 확인합니다. deploy hook은 인증서가 갱신된 뒤 Nginx가 새 파일을 읽도록 합니다. 이 구성에서는 인증서 갱신 때문에 Spring Boot를 재시작할 필요가 없습니다.

```text
sudo certbot renew --dry-run
```

설치 방식이 제공한 timer 또는 cron이 실제 등록되어 있는지 확인합니다. 기본 dry-run이 deploy hook까지 실행한다고 가정하지 말고, hook도 별도로 검사합니다. Certbot이 지원하는 `--run-deploy-hooks` 옵션을 사용할 경우 동작을 확인하고 실행합니다. [Certbot 갱신과 hook](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates)

### 확장 09. 외부에서 장애와 만료를 감시하기

#### 09-1. 외부 감시가 필요한 이유

앱 서버 안의 프로세스만 감시하면 서버 전체가 멈췄을 때 알림도 멈출 수 있습니다. 이 예제는 별도 Linux 호스트에서 공개 HTTPS 건강 상태와 인증서 만료를 확인합니다.

알림 대상은 Slack Incoming Webhook의 `text` JSON 형식을 사용하는 예시입니다. 실제 webhook은 별도로 발급받아 비밀 설정으로 등록합니다. 이 문서를 작성하는 과정에서는 메시지를 전송하지 않습니다.

#### 09-2. 감시 스크립트

감시 호스트의 `/opt/board-monitor/monitor.py`:

```python
import json
import os
import socket
import ssl
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from pathlib import Path

url = os.environ["MONITOR_URL"]
webhook = os.environ["ALERT_WEBHOOK_URL"]
state_file = Path("/var/lib/board-monitor/state.json")
parsed = urllib.parse.urlparse(url)
if parsed.scheme != "https" or not parsed.hostname:
    raise SystemExit("MONITOR_URL must be HTTPS")

issues = []
try:
    with urllib.request.urlopen(url, timeout=10) as response:
        data = json.load(response)
        if data.get("status") != "UP":
            issues.append("health-not-up")
except Exception as exc:
    issues.append("health-" + type(exc).__name__)

try:
    context = ssl.create_default_context()
    with socket.create_connection((parsed.hostname, parsed.port or 443), timeout=10) as raw:
        with context.wrap_socket(raw, server_hostname=parsed.hostname) as connection:
            expires = ssl.cert_time_to_seconds(connection.getpeercert()["notAfter"])
    if expires - time.time() < 21 * 86400:
        issues.append("certificate-expires-within-21-days")
except Exception as exc:
    issues.append("tls-" + type(exc).__name__)

state = json.loads(state_file.read_text()) if state_file.exists() else {
    "failures": 0, "alerted": ""
}
state["failures"] = state["failures"] + 1 if issues else 0
current = ",".join(sorted(issues))
message = None
if issues and state["failures"] >= 2 and current != state["alerted"]:
    message = "[Spring Board] Problem: " + current
elif not issues and state["alerted"]:
    message = "[Spring Board] Recovered"

if message:
    body = json.dumps({"text": message}).encode("utf-8")
    request = urllib.request.Request(webhook, data=body,
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            response.read()
    except Exception:
        print("Alert delivery failed", file=sys.stderr)
        raise SystemExit(1)
    state["alerted"] = current

state_file.parent.mkdir(parents=True, exist_ok=True)
with tempfile.NamedTemporaryFile(mode="w", dir=state_file.parent,
                                 encoding="utf-8", delete=False) as temp:
    json.dump(state, temp)
    temporary_path = temp.name
os.replace(temporary_path, state_file)
```

1. 두 번 연속 이상이 확인되면 알립니다.
2. 같은 이상 상태가 유지될 때는 중복 알림을 보내지 않습니다.
3. 정상으로 돌아오면 복구 알림을 보냅니다.
4. 인증서는 연결 시 신뢰·호스트 이름을 검증하고 만료 21일 이내를 경고합니다.
5. 전송에 실패하면 알림 완료 상태를 기록하지 않아 다음 실행에 다시 시도합니다.

상태 파일이 유실되면 기존 상태를 기억하지 못하므로 중복 알림이 나올 수 있습니다. 이 간단한 감시는 기능별 합성 테스트나 모니터링 서비스의 가용성까지 보장하지 않습니다. 실제 FCM 푸시를 정기 건강 검사로 보내지는 않습니다.

#### 09-3. systemd timer로 반복 실행하기

감시 호스트에 `boardmonitor` 계정을 준비합니다. 설정 파일 `/etc/board-monitor.env`는 해당 서비스가 읽을 수 있게 하고 일반 사용자에게 공개하지 않습니다.

```text
MONITOR_URL=https://board.example.com/actuator/health
ALERT_WEBHOOK_URL=실제_Slack_Incoming_Webhook_URL
```

`/etc/systemd/system/board-monitor.service`:

```ini
[Unit]
Description=Check Spring Board externally

[Service]
Type=oneshot
User=boardmonitor
EnvironmentFile=/etc/board-monitor.env
StateDirectory=board-monitor
ExecStart=/usr/bin/python3 /opt/board-monitor/monitor.py
TimeoutStartSec=60
NoNewPrivileges=true
```

`/etc/systemd/system/board-monitor.timer`:

```ini
[Unit]
Description=Check Spring Board every five minutes

[Timer]
OnBootSec=1min
OnUnitActiveSec=5min

[Install]
WantedBy=timers.target
```

파일을 준비한 후 감시 호스트에서 적용합니다.

```text
sudo systemctl daemon-reload
sudo systemctl enable --now board-monitor.timer
```

최초에는 검증용 서버를 대상으로 수동 실행과 알림 수신을 확인합니다. 운영 서버를 일부러 중지하지 않고 검증 서버의 장애·복구로 시험합니다. 감시 스크립트 실패와 webhook 전송 실패는 감시 호스트의 journal에서도 확인합니다.

## 4. 코드 주요 라인 설명

1. 워크플로 `working-directory`: 여러 장 중 실제 빌드할 프로젝트를 지정합니다.
2. `test -f`: 앞 장의 회귀 테스트가 빠진 상태를 검증 성공으로 넘기지 않습니다.
3. `needs: verify`: 검증 성공을 배포의 선행 조건으로 둡니다.
4. `environment: production`: GitHub에 설정한 승인과 환경 Secrets를 적용합니다.
5. `StrictHostKeyChecking=yes`: 등록한 서버 호스트 키로 SSH 대상을 검증합니다.
6. 배포 스크립트 `flock`: 같은 서버에 동시에 배포하지 않도록 잠급니다.
7. `X-App-Release`: 실제 새 버전이 응답하는지 확인합니다.
8. `FLASHBACK_TIME`: 논리 백업의 일관된 시점을 지정합니다.
9. Certbot deploy hook: 갱신 후 Nginx가 인증서를 다시 읽게 합니다.
10. 감시 스크립트 `alerted`: 동일한 장애의 반복 알림을 줄입니다.

## 5. 실행 순서

### VS Code와 GitHub

1. 앞 장의 회귀 테스트를 로컬에서 통과시킵니다.
2. Actuator·proxy Profile·배포 ID 헤더와 워크플로를 추가합니다.
3. PR을 만들어 Actions의 verify 결과와 테스트 보고서를 확인합니다.
4. 검증 Oracle DB에서 Flyway 변경을 확인합니다.
5. 서버 준비와 production 보호 규칙을 완료합니다.
6. main의 워크플로를 수동 실행하고 백업·변경 검토 후 승인합니다.

### 운영 서버

1. Java·systemd·계정·환경 파일·디렉터리를 준비합니다.
2. Nginx와 최초 인증서 발급을 완료합니다.
3. 배포 스크립트를 설치하고 제한된 재시작 권한을 설정합니다.
4. 승인된 배포 후 공개 도메인에서 실제 기능을 확인합니다.
5. 백업·복구 검증과 Certbot 자동 갱신을 확인합니다.
6. 별도 감시 호스트에서 알림과 복구 알림을 검증합니다.

## 6. 예상 결과와 회귀 확인표

| 확인 항목 | 기대 결과 |
| --- | --- |
| PR 테스트 실패 | verify 실패, 배포 없음 |
| Push 성공 | 테스트 보고서와 검증 Jar 생성, 자동 운영 배포 없음 |
| 수동 실행 | main 검증 후 승인 대기 |
| 승인 거부 | 운영 서비스 변경 없음 |
| 승인 완료 | 같은 실행의 Jar 업로드·재시작·버전 검사 |
| 배포 실패 | 작업 실패와 서비스 로그 확인, DB 자동 롤백 없음 |
| DB 논리 백업 | dump·로그·해시와 외부 복사 확인 |
| 복구 검증 | 별도 DB에서 계정·게시글·Flyway 이력 확인 |
| 인증서 갱신 | 검증 성공 후 Nginx가 새 인증서 사용 |
| 외부 감시 | 장애 두 번 확인 후 알림, 회복 후 복구 알림 |
| 로그인·권한 | 기존 일반·관리자 계정 구분 유지 |
| 게시판 | CRUD·검색·페이징·입력 검증 유지 |
| Firebase | 비활성 상태 안내 또는 준비된 환경에서 실통신 성공 |
| WebJars와 폼 | HTTPS에서 정적 파일·CSRF 요청 정상 |

이 확인표는 실제 실행할 항목입니다. 워크플로 파일이 있다는 사실만으로 CI·승인·백업·알림이 검증되었다고 기록하지 않습니다. 테스트 결과, 승인 기록, 복구 기록, 인증서 만료일과 알림 수신 결과를 남깁니다.

## 7. 마무리

이번 장에서는 예제를 운영하는 과정을 연결했습니다. 코드 검증은 CI에서 반복하고, 운영 변경은 승인 후 적용하며, 백업은 복구로 확인하고, 인증서와 서비스 상태는 외부에서 계속 점검합니다.

다음 확장에서는 무중단 배포, DB 변경의 이전 버전 호환성, 백업의 목표 복구 시간과 데이터 손실 범위, 장애 대응 절차를 더 구체화할 수 있습니다. 기존 게시판과 관리자 화면의 동작을 기준으로 작은 변경부터 검증하는 원칙은 그대로 유지합니다.
