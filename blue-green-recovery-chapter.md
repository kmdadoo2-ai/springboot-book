# 확장 학습. 무중단 배포와 DB 호환성, 복구 목표, 장애 대응

앞 장의 `ci-operations-chapter.md`에 이어지는 원고입니다. 기존 게시판·관리자 화면을 유지하면서 배포 중단을 줄이고, 장애가 발생했을 때 복구하는 절차를 만듭니다. Java 21 이상, Spring Boot 4.1.1, Gradle, Oracle, Thymeleaf를 기준으로 합니다.

VS Code에서는 예제 파일을 편집하고 GitHub와 브라우저에서 결과를 확인합니다. systemd·Nginx 명령은 Windows가 아닌 Linux 운영 또는 검증 서버에서 실행합니다. 아래 내용은 교재 예제이며 실제 서버에 자동 적용되지 않습니다.

## 1. 장의 학습 목표

1. 새 앱을 별도 포트에서 준비한 뒤 요청을 전환할 수 있습니다.
2. 두 앱 사이에서 로그인 세션을 공유하고 기존 폼을 검증할 수 있습니다.
3. 구버전과 신버전이 함께 사용할 수 있는 DB 변경을 설계할 수 있습니다.
4. 목표 복구 시간과 허용 데이터 손실을 정하고 훈련에서 측정할 수 있습니다.
5. 장애 감지·판단·복구·회귀 검사·기록의 순서로 대응할 수 있습니다.

## 2. 핵심 개념 설명

### 블루·그린 배포

현재 요청을 처리하는 앱을 Blue, 새 버전을 준비하는 앱을 Green이라고 부릅니다. 새 앱의 검사가 끝나면 Nginx의 연결 대상을 바꿉니다. 다음 배포에서는 두 역할이 바뀝니다.

```text
브라우저 -- HTTPS --> Nginx -- 현재 요청 --> Blue  127.0.0.1:8081
                          -- 전환 후보 --> Green 127.0.0.1:8082
                                               |
                           두 앱이 같은 Oracle과 Redis 사용
```

이 예제는 한 Linux 서버에 앱 두 개를 실행합니다. **앱 교체 중 중단을 줄이는 구성이지, 서버·Nginx·DB·Redis 장애까지 해결하는 고가용성 구성은 아닙니다.** 두 JVM과 두 커넥션 풀을 감당할 메모리·CPU·DB 연결 여유도 필요합니다.

### 세 가지 조건

1. 새 앱이 준비된 다음 요청을 보냅니다.
2. 기존 요청이 끝날 때까지 이전 앱을 유지합니다.
3. 세션·DB·정적 파일·요청 형식이 전환 전후에 호환되어야 합니다.

단순히 포트만 바꾸면 메모리 안의 로그인 세션이 사라질 수 있습니다. 또한 오래 열어 둔 폼은 구버전 HTML이므로 신버전에서도 제출할 수 있어야 합니다.

### RTO와 RPO

| 용어 | 의미 | 이 교재의 훈련 목표 예시 |
| --- | --- | --- |
| RTO | 장애 발생 후 서비스 복구까지 허용하는 시간 | 30분 이내 |
| RPO | 장애 시 허용하는 데이터 손실의 시간 범위 | 15분 이내 |

이 값은 설정만 하면 보장되는 수치가 아닙니다. 사용자 요구와 비용을 고려해 정하고 복구 훈련으로 검증합니다. 이 장에서는 RTO에 감지·판단·복원·기능 검사 시간까지 포함합니다. [Oracle의 RTO·RPO 설명](https://docs.oracle.com/en-us/iaas/mysql-database/doc/recovery-time-objective-rto-recovery-point-objective-rpo.html)

## 3. 단계별 예제 코드

### 확장 10. 로그인 상태를 유지하며 두 앱 준비하기

#### 10-1. 앞 장에서 유지할 것과 교체할 것

| 앞 장 구성 | 이번 장 처리 |
| --- | --- |
| CI의 test·bootJar·검증 Jar·승인 | 유지 |
| 단일 current.jar와 spring-board.service 재시작 | 최초 이전 후 슬롯별 실행으로 교체 |
| Nginx 인증서와 Certbot 갱신 | 유지 |
| RequestLogFilter의 X-Request-ID·X-App-Release | 유지 |
| Oracle·Firebase 비밀 설정 | 같은 환경 값을 두 앱에 적용 |
| /actuator/health GET만 공개 | 유지 |
| 백업·외부 장애 감시 | 유지하고 복구 훈련 추가 |

앞 장의 단일 서버 `deploy.sh`와 새 전환 절차를 동시에 실행하지 않습니다. 이전 작업 중에는 GitHub 운영 배포를 잠시 중지하고, 아래 검증을 완료한 뒤 새 절차를 승인 과정에 연결합니다.

#### 10-2. Redis 세션 의존성 추가하기

`chapter13/spring/build.gradle`의 기존 dependencies 안에 추가합니다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-session-data-redis'
```

Spring Boot의 의존성 관리에 맡기므로 버전을 별도로 지정하지 않습니다. [Spring Session의 Boot 연동](https://docs.spring.io/spring-session/reference/guides/boot-redis.html)

Redis는 운영자가 먼저 준비합니다. 공용 인터넷에 노출하지 않고 ACL·비밀번호·접근 네트워크를 제한합니다. 아래는 같은 서버 loopback Redis 예제입니다. 원격 Redis는 제공자의 TLS 설정도 적용해야 합니다.

`src/main/resources/application-proxy.properties`의 기존 보안·Actuator 설정을 유지하며 추가합니다.

```properties
spring.data.redis.host=${REDIS_HOST:127.0.0.1}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.username=${REDIS_USERNAME:default}
spring.data.redis.password=${REDIS_PASSWORD}
spring.data.redis.connect-timeout=2s
spring.data.redis.timeout=2s
spring.session.data.redis.namespace=board:prod:session
spring.session.timeout=30m
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

1. 두 앱은 같은 Redis·DB 번호·namespace·쿠키 설정을 사용합니다.
2. namespace에는 blue나 green을 넣지 않습니다. 구분하면 세션을 공유하지 못합니다.
3. Spring Boot 4.1 기준 Redis 세션 설정 접두사는 `spring.session.data.redis`입니다.
4. 기존 HTTPS용 Secure·HttpOnly·SameSite 설정도 유지합니다.
5. 종료 시 처리 중인 요청에 시간을 주되, 타임아웃 이후까지 완료를 보장하지는 않습니다.

설정 키는 [Spring Boot 속성 문서](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)를 기준으로 하며 종료 방식은 [Graceful Shutdown 문서](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html)를 참고합니다.

`/etc/spring-board/app.env`에 실제 REDIS_PASSWORD를 추가합니다. 기존 DB 비밀번호처럼 Git에 올리지 않습니다. Redis 연결이 없으면 로그인 등 세션 기능이 실패할 수 있습니다. dev 실행에도 이 의존성이 영향을 주므로 로컬 Redis를 준비하고 해당 Profile에 연결 설정을 적용합니다.

**최초 도입 주의:** 기존 메모리 세션을 Redis로 자동 이전하지 않습니다. 첫 도입은 계획된 전환으로 진행하고 재로그인을 안내합니다. 이후 동일한 Redis 세션 구성을 가진 버전 사이에서 세션 유지 배포를 검증합니다.

세션 속성은 기본적으로 Java 직렬화를 사용합니다. 커스텀 사용자 객체나 라이브러리 버전 변경은 역직렬화 호환성을 별도로 확인합니다. 프레임워크 업그레이드와 업무 기능 변경을 한 번에 섞지 않습니다. [Spring Session 직렬화 설정](https://docs.spring.io/spring-session/reference/configuration/redis.html)

#### 10-3. 기존 CI에도 Redis 추가하기

앞 장 워크플로의 `jobs.verify` 바로 아래에 다음 services를 추가합니다. `steps` 안에 넣지 않습니다.

```yaml
services:
  redis:
    image: redis:7.4
    ports:
      - 6379:6379
    options: >-
      --health-cmd "redis-cli ping"
      --health-interval 5s
      --health-timeout 3s
      --health-retries 10
```

같은 verify의 기존 env에 추가합니다.

```yaml
SPRING_DATA_REDIS_HOST: 127.0.0.1
SPRING_DATA_REDIS_PORT: "6379"
SPRING_DATA_REDIS_PASSWORD: ""
SPRING_SESSION_DATA_REDIS_NAMESPACE: board:ci:session
```

이는 GitHub-hosted 임시 테스트 서비스입니다. 운영 Redis를 연결하거나 운영 비밀번호를 전달하지 않습니다. 운영 이미지 정책에 맞춰 검토한 태그·digest로 고정합니다. 기존 H2·Firebase 비활성·회귀 테스트 설정은 그대로 유지합니다.

#### 10-4. 슬롯별 환경과 서비스 만들기

다음 두 디렉터리를 운영자가 준비합니다.

```text
/opt/spring-board/slots/blue/current.jar
/opt/spring-board/slots/green/current.jar
/etc/spring-board/blue.env
/etc/spring-board/green.env
```

각 current.jar는 앞 장에서 체크섬을 확인한 `/opt/spring-board/releases/배포ID/spring-boot-study.jar`를 가리키는 심볼릭 링크입니다. 실행 중인 슬롯의 링크를 덮어쓰지 않습니다. 환경 파일은 실행 계정이 읽고 운영자만 변경하도록 제한합니다.

`blue.env` 예시:

```text
SERVER_PORT=8081
APP_RELEASE_ID=검증한_Blue_배포ID
APP_LOG_FILE=/var/log/spring-board/blue.log
```

`green.env` 예시:

```text
SERVER_PORT=8082
APP_RELEASE_ID=검증한_Green_배포ID
APP_LOG_FILE=/var/log/spring-board/green.log
```

APP_RELEASE_ID는 CI에서 검증한 Jar의 배포 ID를 그대로 사용합니다. 슬롯 이름과 버전 ID를 혼동하지 않습니다. 두 앱이 하나의 회전 로그 파일에 동시에 쓰지 않도록 경로도 구분합니다.

`/etc/systemd/system/spring-board@.service`:

```ini
[Unit]
Description=Spring Board slot %i
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=springboard
Group=springboard
WorkingDirectory=/opt/spring-board
EnvironmentFile=/etc/spring-board/app.env
EnvironmentFile=/etc/spring-board/%i.env
ExecStart=/usr/bin/java -jar /opt/spring-board/slots/%i/current.jar
Restart=on-failure
RestartSec=5
TimeoutStopSec=90
UMask=0027
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

1. `%i`에 blue 또는 green이 들어갑니다.
2. 공통 파일의 `SPRING_PROFILES_ACTIVE=prod,proxy`를 유지합니다.
3. 뒤에서 읽는 슬롯 환경 파일이 포트·버전·로그 경로를 구분합니다.
4. 앞 장의 공용 release.env는 이 서비스에서 읽지 않습니다.
5. 실제 Java 경로와 계정 권한을 확인합니다. 배포 사용자에게 임의 서비스 제어 권한을 주지 않습니다.

운영자가 파일과 링크를 준비한 뒤 실행합니다.

```bash
sudo systemctl daemon-reload
sudo systemctl start spring-board@blue.service
sudo systemctl start spring-board@green.service
```

이 시점에는 Nginx의 연결 대상을 바꾸지 않았으므로 기존 서비스가 계속 공개 요청을 처리합니다. 예약 작업·데이터 초기화·메시지 소비가 있다면 두 앱에서 중복 실행되지 않도록 별도로 분리합니다. 기존 prod에서 개발용 계정 초기화가 실행되지 않는지도 확인합니다.

### 확장 11. 요청 전환과 복귀 절차 만들기

#### 11-1. Nginx upstream 준비하기

Nginx의 `http` 문맥에서 한 번만 읽히는 파일, 예를 들어 `/etc/nginx/conf.d/board-upstream.conf`를 추가합니다. 배포판의 실제 include 구성을 먼저 확인합니다.

```nginx
upstream board_backend {
    include /etc/nginx/board/active-server.conf;
}
```

`/etc/nginx/board/active-server.conf` 초기 내용:

```nginx
server 127.0.0.1:8081;
```

앞 장 HTTPS server 블록의 location 안에서 proxy_pass 한 줄만 교체합니다.

```nginx
proxy_pass http://board_backend;
```

기존 인증서 경로·전달 헤더 덮어쓰기·HTTP 인증서 검증 경로는 유지합니다. 처음 8080에서 8081로 옮길 때도 아래 검사를 수행하고, 기존 8080 프로세스를 바로 종료하지 않습니다.

#### 11-2. 후보 앱을 직접 확인하기

공개 전환 전에는 서버 내부에서 후보의 건강 상태와 배포 ID를 확인합니다.

```bash
curl --fail --show-error --include http://127.0.0.1:8082/actuator/health
```

응답의 `status=UP`와 `X-App-Release`가 **이번에 검증한 배포 ID와 정확히 같은지** 확인합니다. DB·Redis가 정상이어도 모든 화면이 정상임을 뜻하지는 않습니다.

관리자 화면과 로그인·쓰기 검사는 별도 검증 환경에서 먼저 실행합니다. 운영 후보를 직접 브라우저로 검사하려고 8082를 외부에 공개하거나 HTTP에서 Secure 쿠키를 해제하지 않습니다. 필요하면 접근 제한된 HTTPS 검증 프록시를 별도로 준비합니다.

#### 11-3. 운영자가 승인 후 전환하기

이번 실습은 처음부터 복잡한 자동 전환 코드를 만들지 않고 다음 절차를 승인된 운영자가 수행합니다.

1. 현재 활성 슬롯·배포 ID·DB 버전을 기록합니다.
2. 배포 잠금을 확보하고 다른 배포와 Certbot reload 작업이 겹치지 않도록 조정합니다.
3. 후보 앱의 정확한 배포 ID, 건강 상태, 검증 환경 회귀 검사 결과를 확인합니다.
4. `active-server.conf`를 백업하고 Green의 `server 127.0.0.1:8082;`로 변경합니다.
5. `sudo nginx -t`가 성공했을 때만 `sudo systemctl reload nginx`를 실행합니다.
6. 검사 실패 시 이전 파일을 복원합니다. reload 성공 여부가 불명확하면 파일 내용만 보고 활성 슬롯을 판단하지 않습니다.
7. 공개 HTTPS에서 새 연결을 열어 건강 상태·배포 ID·실제 화면을 확인합니다.
8. Nginx 로그와 프로세스에서 이전 worker의 종료, 이전 앱의 진행 중인 요청 종료를 확인합니다.
9. 관찰 기간 동안 이전 앱을 유지하고, 문제가 없을 때만 이전 슬롯을 중지합니다.

Nginx reload는 새 worker를 시작하고 기존 worker가 처리 중인 연결을 마치도록 합니다. 따라서 reload 명령이 끝났다는 이유만으로 이전 앱을 중지하면 안 됩니다. 긴 요청·스트리밍·WebSocket이 있다면 별도 drain 기준이 필요합니다. [Nginx 제어 문서](https://nginx.org/en/docs/control.html)

```bash
sudo systemctl stop spring-board@blue.service
```

이 명령은 위 확인이 끝난 뒤에만 실행합니다. 고정된 10초 대기만으로 안전한 종료라고 판단하지 않습니다. 이후 다음 배포는 비활성 Blue를 새 버전으로 준비합니다. 부팅 시 활성 슬롯이 실행되도록 서비스 enable 정책도 함께 관리합니다.

GitHub에서는 앞 장의 검증·승인·Jar 전달을 유지하되 **기존 deploy.sh 호출을 제거**하고 이 절차를 운영 작업으로 연결합니다. 자동화는 검증된 슬롯 준비·상태 확인·전환 절차를 하나의 제한된 배포 도구로 만든 다음 적용합니다. 이 원고의 수동 절차만으로 GitHub 자동 배포가 완성되지는 않습니다.

#### 11-4. 문제가 있으면 이전 슬롯으로 복귀하기

1. 이전 앱이 현재 DB·세션 형식과 호환되는지 확인합니다.
2. 중지되어 있다면 이전 앱을 시작하고 건강 상태·이전 배포 ID를 확인합니다.
3. active-server.conf를 이전 포트로 되돌리고 설정 검사 후 reload합니다.
4. 공개 화면을 검사하고 문제 버전으로 향하던 요청이 끝날 때까지 기다립니다.
5. 문제 버전의 로그·Jar·DB 이력을 보존합니다.

DB 자체가 잘못 변경되었다면 앱만 되돌려 해결되지 않을 수 있습니다. 데이터가 손상되는 상황에서는 먼저 쓰기 요청을 제한하고 DBA와 복구 방식을 결정합니다. POST·FCM 전송은 결과가 불명확하다고 무조건 재시도하지 않습니다.

### 확장 12. 이전 버전과 호환되는 DB 변경

#### 12-1. 추가하고, 옮기고, 나중에 제거하기

변경은 Expand → Migrate → Contract 단계로 나눕니다. 이번 예제는 첫 단계인 선택적 분류 컬럼 추가입니다. 기존 title·content·writer·createdAt과 API 필드는 그대로 유지합니다.

앞 장 마지막 Flyway가 V2라면 다음 파일을 사용합니다. 이미 V3가 있다면 비어 있는 다음 버전을 사용하고 기존 적용 파일은 수정하지 않습니다.

`src/main/resources/db/migration/V3__add_board_category.sql`:

```sql
ALTER TABLE board ADD (category VARCHAR2(30 CHAR));
```

1. nullable 컬럼이라 구버전 INSERT가 값을 보내지 않아도 됩니다.
2. 구버전은 자신이 알고 있는 컬럼만 계속 사용합니다.
3. 변경 전 실제 테이블 이름·매핑과 DDL 잠금 영향을 검증 DB에서 확인합니다.
4. Oracle DDL은 암묵적 커밋이 발생할 수 있으므로 앱 트랜잭션으로 되돌릴 수 있다고 가정하지 않습니다.

Flyway 적용은 승인된 한 번의 변경 단계로 관리합니다. 이 소규모 예제에서는 호환성 검사를 마친 뒤 후보 앱 하나의 시작 과정에서 실행할 수 있습니다. 큰 변경은 DBA 승인 절차로 분리합니다. **후보 앱 시작에 실패했더라도 DB 변경은 이미 적용되었을 수 있습니다.** 이때 Flyway 이력과 스키마부터 확인합니다.

#### 12-2. Board에 선택적 필드 추가하기

기존 `com.study.spring.board.Board`의 필드 영역에 추가합니다. 생성자·update 메서드·컨트롤러·DTO는 교체하지 않습니다.

```java
@Column(length = 30)
private String category;

public String getCategoryLabel() {
    return category == null ? "일반" : category;
}
```

기존 등록은 계속 null을 저장할 수 있습니다. 분류 표시가 필요한 화면에서만 `board.categoryLabel`을 사용합니다. 기존 게시판을 유지하는 것이 목적이므로 이번 단계에서는 입력 폼이나 REST 응답에 필수 필드를 추가하지 않습니다.

기존 데이터 채우기가 필요하다면 별도 승인한 다음 마이그레이션에서 수행합니다.

```sql
UPDATE board SET category = '일반' WHERE category IS NULL;
```

큰 테이블은 일괄 UPDATE의 잠금·UNDO·실행 시간을 검증하고 배치 작업으로 나눕니다. 이 작업 뒤에도 구버전은 null을 새로 넣을 수 있으므로, 구버전 종료와 모든 쓰기 경로 변경 전에는 NOT NULL을 적용하지 않습니다.

#### 12-3. 이름 변경은 한 번에 하지 않기

title을 subject로 바꾸고 싶어도 첫 배포에서 title을 삭제하지 않습니다. 새 컬럼을 추가한 뒤 구버전이 쓰는 title을 기준으로 읽거나 동기화하는 전환 단계를 둡니다. 구버전 종료 후 최종 데이터 동기화를 수행하고, 롤백 가능 기간이 끝난 다음 제거를 별도 배포합니다.

신버전만 양쪽에 저장해도 구버전이 title만 수정하면 값이 달라질 수 있습니다. 따라서 이중 쓰기 자체를 완전한 해결책으로 간주하지 않습니다. 읽기 우선순위·충돌 처리·최종 동기화까지 설계합니다.

| 조합 | 검증 기준 |
| --- | --- |
| 구버전 앱 + 변경 전 DB | 기준 회귀 검사 통과 |
| 구버전 앱 + 확장 DB | 기존 CRUD·검색·페이징 정상 |
| 신버전 앱 + 확장 DB | null 기존 데이터와 신규 데이터 정상 |
| 구버전 작성 후 신버전 조회 | 내용과 작성자 유지 |
| 신버전 작성 후 구버전 수정 | 기존 필드 정상, 추가 데이터 손실 여부 확인 |
| 구버전 세션·폼 + 신버전 | 로그인 유지, CSRF 검증과 폼 제출 정상 |

H2 테스트만으로 Oracle DDL·잠금·기존 데이터 호환성을 검증했다고 기록하지 않습니다. 같은 스키마를 쓰는 두 버전으로 검증 DB에서 위 조합을 확인합니다.

### 확장 13. RTO·RPO를 복구 훈련으로 확인하기

#### 13-1. 백업 주기와 복구 지점을 구분하기

하루 한 번의 Data Pump만으로 RPO 15분을 약속할 수 없습니다. 마지막 복구 가능한 시점이 전날이면 그 이후 데이터가 손실될 수 있습니다. 복사 지연·백업 실패·로그 누락도 고려해야 합니다.

Oracle의 시점 복구가 필요하면 DBA가 RMAN 백업, ARCHIVELOG, 아카이브 로그 보존·외부 복사·복구 체인을 설계합니다. ARCHIVELOG를 켠 사실만으로 목표 RPO가 충족되지는 않습니다. [Oracle 백업·복구와 가용성 지침](https://docs.oracle.com/database/121/HABPT/E40019-02.pdf)

DB뿐 아니라 Jar·설정·암호화 키·인증서 재발급 수단도 복구할 수 있어야 합니다. 비밀값은 저장소와 별개의 통제된 비밀 관리·백업 경로로 관리합니다. Redis 세션 유실은 재로그인에 영향을 주며, Firestore 데이터는 Oracle 백업에 포함되지 않습니다.

#### 13-2. 검증 DB에서 복구 훈련하기

1. 검증 환경에 테스트용 게시글을 시간 간격을 두고 등록합니다.
2. 각 게시글의 ID와 DB 커밋 완료 시각을 UTC로 별도 기록합니다.
3. 장애를 가정한 시각을 기록하고 검증 앱의 쓰기를 중지합니다.
4. 별도 복구 DB에 백업과 필요한 로그를 복구합니다. 운영 DB에 덮어쓰지 않습니다.
5. DBA가 복구 지점·DB 상태·제약 조건·Flyway 이력을 확인합니다.
6. 복구한 DB에 맞는 Jar를 연결하고 Firebase 실제 발송은 비활성화합니다.
7. 로그인·게시글·검색·페이징·관리자 화면을 검사합니다.
8. 기능 검사 완료 시각과 마지막으로 확인된 커밋 시각을 기록합니다.

Data Pump 복구 명령은 앞 장의 별도 스키마 복구 예제를 사용합니다. RMAN 복구 명령은 DB 구성·백업 위치·목표 SCN에 따라 달라지므로 운영 DB에 적용할 범용 복원 명령으로 대체하지 않습니다.

#### 13-3. 훈련 결과 계산하기

VS Code에서 `recovery-drill.json` 같은 기록 파일을 작성할 수 있습니다. 다음 값은 실제 결과가 아닌 예시입니다.

```json
{
  "incidentId": "DRILL-001",
  "incidentAt": "2026-09-13T01:00:00Z",
  "detectedAt": "2026-09-13T01:04:00Z",
  "serviceVerifiedAt": "2026-09-13T01:27:00Z",
  "lastRecoverableCommitAt": "2026-09-13T00:52:00Z",
  "targetRtoMinutes": 30,
  "targetRpoMinutes": 15,
  "observedRecoveryMinutes": 27,
  "observedLossWindowMinutes": 8,
  "result": "PASS"
}
```

1. 관측 복구 시간은 기능 검증 완료 시각에서 장애 시각을 뺍니다.
2. 관측 손실 범위는 장애 시각과 복구 가능한 마지막 커밋 시각의 차이입니다.
3. 예시에서는 각각 27분과 8분으로 목표 이내입니다.
4. 실제 복구 지점은 DBA의 로그·SCN 검증과 대조합니다. 마지막 게시글 하나만으로 모든 테이블의 복구 일관성을 증명하지 않습니다.
5. 목표를 초과하면 FAIL로 기록하고 복원 속도·백업 간격·알림 지연 등을 개선합니다.

앞 장 감시는 5분 간격으로 두 번 확인하므로 감지에 대략 5~10분과 요청 처리 시간이 걸릴 수 있습니다. RTO 30분 중 상당 부분을 감지에 사용할 수 있으므로 실제 측정 후 감시 간격과 오탐 정책을 조정합니다.

### 확장 14. 장애 대응 절차와 기록 만들기

#### 14-1. 먼저 피해를 줄이고 증거를 남기기

| 순서 | 담당 | 행동 |
| --- | --- | --- |
| 감지 | 당직 담당 | 알림·외부 접속으로 영향 범위 확인 |
| 선언 | 대응 책임자 | 장애 ID 생성, 담당 지정, 신규 배포 중지 |
| 진단 | 앱 담당·DBA | 배포 ID·요청 ID·DB·Redis·인증서 상태 확인 |
| 완화 | 승인된 운영자 | 호환 가능한 이전 슬롯 복귀 또는 쓰기 제한 |
| 복구 | 앱 담당·DBA | 앱 복구 또는 승인된 데이터 복원 |
| 검증 | 검증 담당 | 공개 화면과 회귀 확인표 점검 |
| 종료 | 대응 책임자 | 관찰 기간 후 복구 공지, 타임라인 확정 |

혼자 실습하더라도 역할을 나누어 기록합니다. 원인이 밝혀지지 않았다는 이유로 사용자 영향 안내를 미루지 않으며, 확인되지 않은 원인을 단정하지 않습니다.

#### 14-2. 증상별 첫 확인

| 증상 | 확인 | 피할 행동 |
| --- | --- | --- |
| 배포 직후 502 | 대상 포트·서비스 로그·배포 ID | 두 슬롯을 동시에 재시작 |
| 세션 소실·로그인 반복 | Redis·namespace·쿠키·직렬화 | 운영 Redis 전체 삭제 |
| DB 오류 | 연결·잠금·Flyway 이력·남은 공간 | 마이그레이션 이력 임의 삭제 |
| TLS 오류 | 실제 제공 인증서·만료·Nginx reload | 클라이언트 인증서 검증 해제 |
| Firebase 오류 | 기능 설정·자격 증명·외부 응답 | FCM 요청 무제한 재전송 |
| 데이터 오염 | 쓰기 차단 범위·최초 발생 시각 | 승인 없이 운영 백업 덮어쓰기 |

로그에는 요청 ID·배포 ID·오류 종류를 남기되 비밀번호·세션 ID·토큰·FCM 등록 토큰을 사건 기록에 복사하지 않습니다. 관리자 화면 전체를 공개하지도 않습니다.

#### 14-3. 장애 기록 양식

```text
장애 ID:
발생 / 감지 / 기능 검증 완료 시각(UTC):
영향 기능과 사용자 범위:
활성 슬롯 / 배포 ID / Flyway 버전:
확인된 사실:
아직 확인하지 못한 사항:
완화 조치 / 승인자 / 수행 시각:
데이터 복원 여부 / 복구 지점 / 손실 범위:
회귀 검사 결과와 증거 위치:
재발 방지 작업 / 담당 / 완료 기한:
```

복구 후에는 담당자를 비난하는 기록 대신 감지·검증·절차가 부족했던 지점을 찾습니다. 재발 방지 작업에는 회귀 테스트나 알림 개선처럼 완료 여부를 확인할 수 있는 항목을 넣습니다.

## 4. 코드 주요 라인 설명

아래 번호는 각 예제의 핵심 구문을 가리킵니다. 실행 파일에는 설명용 줄 번호를 넣지 않습니다.

1. `spring-boot-starter-session-data-redis`: 두 앱이 사용하는 HttpSession 저장소를 Redis로 연결합니다.
2. `spring.session.data.redis.namespace`: 공유할 세션 키 공간을 맞춥니다.
3. `EnvironmentFile=/etc/spring-board/%i.env`: 슬롯별 포트·버전·로그 설정을 적용합니다.
4. `proxy_pass http://board_backend`: 직접 포트 대신 전환 가능한 upstream을 사용합니다.
5. `include /etc/nginx/board/active-server.conf`: 활성 앱 주소를 한 곳에서 관리합니다.
6. `ALTER TABLE ... ADD`: 구버전 필드를 제거하지 않고 선택적 컬럼을 추가합니다.
7. `category == null`: 구버전이 만든 null 데이터도 표시할 수 있게 합니다.
8. `serviceVerifiedAt`: 프로세스 시작이 아니라 기능 검증까지 끝난 시각을 기록합니다.

## 5. 실행 방법

### 개발·검증 환경

1. 앞 장의 테스트·Flyway·Actuator·배포 ID 코드를 적용했는지 확인합니다.
2. Redis 의존성·환경 설정·CI 서비스를 추가하고 기존 회귀 테스트를 실행합니다.
3. 구버전과 신버전 모두 Redis 세션 기반인 상태로 두 Jar를 준비합니다.
4. 검증 Oracle에 확장 마이그레이션을 적용하고 두 버전 호환성 표를 검사합니다.
5. 검증 Linux에서 두 슬롯과 Nginx 전환·복귀를 연습합니다.
6. 별도 복구 DB로 백업 훈련을 수행하고 RTO·RPO 결과를 기록합니다.

### 브라우저 회귀 확인

1. 실제 HTTPS 도메인에서 일반 사용자로 로그인합니다.
2. 게시글 목록의 검색어·페이지를 바꾸고 수정 폼을 열어 둡니다.
3. 운영자가 검증된 후보로 전환한 뒤 같은 브라우저에서 폼을 제출합니다.
4. 재로그인 요구·CSRF 오류·입력값 손실이 없는지 확인합니다.
5. 개발자 도구의 Network에서 새 요청의 X-App-Release를 확인합니다.
6. 관리자 계정은 별도 브라우저 프로필에서 관리자·Firebase·환경 화면을 확인합니다.
7. 검증 환경에서 이전 슬롯으로 복귀한 뒤 같은 확인을 반복합니다.

운영에서는 테스트 데이터와 승인된 계정을 사용합니다. FCM은 실제 사용자에게 시험 발송하지 않고 전용 테스트 기기로 제한합니다. 장애·복구 알림 훈련은 검증 환경에서 수행합니다.

## 6. 예상 결과와 회귀 확인표

| 항목 | 통과 기준 |
| --- | --- |
| 후보 준비 실패 | 기존 슬롯으로 계속 서비스, 전환하지 않음 |
| Nginx 검사 실패 | 기존 설정 복원, 실패한 설정 reload 금지 |
| 정상 전환 | 새 요청의 배포 ID 일치, 이전 요청 완료 |
| 로그인·열려 있던 폼 | 세션 유지, CSRF 정상, 작성 내용 보존 |
| 게시판 | CRUD·검색·페이징·유효성 검사 정상 |
| REST API | 기존 경로·인증·응답 필드·오류 계약 유지 |
| 관리자·Firebase | 권한 구분과 비활성 안내 또는 승인된 실통신 정상 |
| DB 호환성 | 구버전·신버전 모두 확장 스키마에서 동작 |
| 슬롯 복귀 | 현재 스키마와 호환되는 이전 앱으로 복귀 성공 |
| 백업 훈련 | 별도 DB에서 데이터·계정·Flyway·기능 검증 |
| RTO·RPO | 실제 측정값과 목표 비교 결과 기록 |
| 장애 대응 | 담당·승인·타임라인·회귀 결과·후속 작업 기록 |

실습 파일 작성만으로 무중단이나 RTO·RPO 달성을 선언하지 않습니다. 부하·긴 요청·세션 호환성·DB 변경을 포함한 실제 검증 결과를 남겨야 합니다.

## 7. 마무리와 다음 학습

이번 장에서는 새 버전을 준비한 뒤 요청을 전환하고, 두 버전이 함께 사용하는 세션과 DB를 보호하며, 복구 목표를 실제 훈련으로 확인했습니다. 장애 대응도 그때그때의 판단에만 맡기지 않고 확인 순서와 기록 양식으로 정리했습니다.

다음에는 다중 서버와 로드밸런서, Redis·DB 고가용성, 부하 시험, 기능별 가용성 목표를 다룰 수 있습니다. 먼저 이번 장의 전환·복귀·복구 훈련을 반복하면서 기존 게시판과 관리자 화면의 동작을 꾸준히 검증합니다.
