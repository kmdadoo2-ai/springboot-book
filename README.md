# 예제로 끝내는 스프링 부트 웹 개발 입문

**저자 김현무 · Spring Boot 예제 저장소 `springboot-book`**

작은 웹 화면에서 시작해 게시판, 로그인, REST API, Firebase 연동과 실행 파일 패키징까지 단계별로 학습하는 예제입니다. 직접 실행하고 수정하면서 Spring Boot 웹 애플리케이션의 흐름을 익혀 보세요.

## 학습 내용

- Thymeleaf 화면 구성과 Form 입력 검증
- JdbcTemplate, MyBatis, Spring Data JPA를 이용한 게시판
- Lombok 리팩토링과 트랜잭션 처리
- 게시글 CRUD, 제목 검색, 페이징과 정렬
- REST API와 Spring Security 기반 로그인·권한 관리
- Bootstrap·WebJars 공통 화면과 외부 라이브러리
- Firebase FCM 전송, Firestore 저장·조회, Auth 사용자 조회
- 실행 가능한 Jar와 개발·운영 Profile 분리

## 개발 환경

최종 예제인 `chapter13/spring` 기준입니다. 장별 의존성은 각 프로젝트의 `build.gradle`을 확인하세요.

| 항목 | 구성 |
| --- | --- |
| Java | Java 21 toolchain |
| Spring Boot | 4.1.1 |
| 빌드 | Gradle Wrapper |
| 웹·검증 | Spring MVC, Thymeleaf, Jakarta Validation |
| 데이터베이스 | Oracle, JDBC 드라이버 `ojdbc11` |
| 데이터 접근 | JdbcTemplate, MyBatis, Spring Data JPA |
| 보안 | Spring Security, Thymeleaf Security Dialect |
| 화면 | Bootstrap 5.3.8, WebJars |
| Firebase | Firebase Admin SDK 9.10.0 |
| 개발 도구 | VS Code |

## 폴더 구성

각 장의 `spring` 폴더는 **별도로 실행하는 Gradle 프로젝트**입니다. 저장소 루트에서 모든 장을 한 번에 실행하는 구조가 아닙니다.

| 폴더 | 주요 내용 |
| --- | --- |
| [chapter01/spring](chapter01/spring) | Spring Boot 시작과 실행 |
| [chapter02/spring](chapter02/spring) | Thymeleaf와 웹 화면 |
| [chapter03/spring](chapter03/spring) | Form과 Validator |
| [chapter04/spring](chapter04/spring) | JdbcTemplate 게시판 |
| [chapter05/spring](chapter05/spring) | MyBatis 게시판 |
| [chapter06/spring](chapter06/spring) | Spring Data JPA |
| [chapter07/spring](chapter07/spring) | Lombok 리팩토링 |
| [chapter08/spring](chapter08/spring) | 트랜잭션 |
| [chapter09/spring](chapter09/spring) | 게시판 REST API |
| [chapter10/spring](chapter10/spring) | Spring Security와 DB 기반 로그인 |
| [chapter11/spring](chapter11/spring) | Bootstrap 공통 화면 |
| [chapter12/spring](chapter12/spring) | Firebase와 관리자 웹 실습 |
| [chapter13/spring](chapter13/spring) | WebJars, 외부 라이브러리, Jar와 Profile |
| [chapter14/spring](chapter14/spring) | DB 변경, HTTPS와 요청 로그, Jar와 Profile |

처음 학습한다면 `chapter01`부터 진행하세요. 다음 실행 안내는 뒤쪽 장까지 반영된 **`chapter13/spring` 기준**입니다. 앞쪽 장은 해당 프로젝트의 설정 파일에 맞춰 실행 환경을 준비합니다.

## 빠른 시작

### 1. VS Code에서 프로젝트 열기

저장소를 내려받은 뒤 **파일 → 폴더 열기**에서 `chapter13/spring`을 엽니다. `build.gradle`과 `gradlew.bat`가 보이는 폴더입니다.

다음 확장을 준비하고 Java 프로젝트 가져오기가 끝날 때까지 기다립니다.

- Extension Pack for Java
- Gradle for Java
- Spring Boot Extension Pack: Dashboard 등을 사용할 때 선택 설치

별도 Gradle 설치 대신 포함된 Wrapper를 사용합니다. 최초 실행에는 Gradle과 의존성 다운로드를 위한 네트워크 연결이 필요합니다.

### 2. Oracle 준비하기

실습용 Oracle DB와 사용자를 준비합니다. 기존 게시글과 계정을 이어서 사용하려면 앞 장과 같은 JDBC URL·DB 사용자로 연결하세요.

개발 Profile의 `ddl-auto=update`는 스키마를 변경할 수 있으므로 실습용 DB를 사용합니다. 저장소의 SQL 스크립트는 내용을 확인하고 필요한 부분만 적용하세요.

### 3. 환경 변수 등록 후 실행하기

프로젝트 폴더의 VS Code 통합 터미널에서 실행합니다. **아래 DB 주소·사용자·비밀번호는 실제 실습 환경의 값으로 바꾸세요.**

Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:oracle:thin:@localhost:1521/xe'
$env:DB_USERNAME = '실습용_DB_사용자명'
$env:DB_PASSWORD = '실습용_DB_비밀번호'
$env:FIREBASE_ENABLED = 'false'

.\gradlew.bat bootRun
```

macOS / Linux:

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL='jdbc:oracle:thin:@localhost:1521/xe'
export DB_USERNAME='실습용_DB_사용자명'
export DB_PASSWORD='실습용_DB_비밀번호'
export FIREBASE_ENABLED=false

bash ./gradlew bootRun
```

현재 최종 예제의 Firebase 기본값은 활성화 상태입니다. 키를 준비하지 않았다면 **`FIREBASE_ENABLED=false`를 명시**하세요. Firebase 실통신은 사용할 수 없지만 게시판 실습은 진행할 수 있습니다.

위 변수는 해당 터미널에서 시작한 프로그램에 전달됩니다. VS Code Run/Debug로 실행하려면 해당 `launch.json`의 `env` 또는 `envFile`에 같은 값을 등록합니다. 이 설정은 별도로 실행한 `bootRun`이나 `java -jar`에는 자동 적용되지 않습니다.

### 4. 브라우저에서 확인하기

| 주소 | 기능 |
| --- | --- |
| http://localhost:8080/jpa/boards | 게시판 목록·검색·페이징 |
| http://localhost:8080/login | 로그인 |
| http://localhost:8080/admin | 관리자 화면 |
| http://localhost:8080/admin/firebase | Firebase 웹 실습 |
| http://localhost:8080/admin/etc | 문자열 라이브러리·실행 환경 |
| http://localhost:8080/api/boards | 게시판 REST API |

개발 Profile에서 회원 테이블이 비어 있을 때 다음 실습 계정이 생성됩니다.

| 권한 | 아이디 | 비밀번호 |
| --- | --- | --- |
| 일반 사용자 | `user` | `1234` |
| 관리자 | `admin` | `1234` |

회원 데이터가 이미 있으면 초기 계정이 추가되지 않을 수 있으므로 기존 계정을 사용하세요. 위 비밀번호는 로컬 학습용이며 운영 환경에서 사용하지 않습니다.

## Firebase 웹 실습

서버를 중지하고 다음 환경 변수를 등록한 뒤 다시 시작합니다.

| 변수 | 값 |
| --- | --- |
| `FIREBASE_ENABLED` | `true` |
| `FIREBASE_PROJECT_ID` | 실제 Firebase 프로젝트 ID |
| `GOOGLE_APPLICATION_CREDENTIALS` | 서비스 계정 JSON 파일의 절대 경로 |

키 파일은 프로젝트 밖에 보관하고 서버 실행 계정이 읽을 수 있게 합니다. 사용할 기능에 맞춰 Firestore 기본 데이터베이스, Authentication 사용자, FCM API와 수신 클라이언트도 준비합니다.

관리자로 로그인한 뒤 `/admin/firebase`의 버튼으로 실습합니다. 기존 로그인 세션과 CSRF 처리를 사용합니다. 현재 `chapter13`의 API 경로는 다음과 같습니다.

| 메서드 | 주소 | 기능 |
| --- | --- | --- |
| GET | `/api/firebase/status` | 활성화 상태 |
| GET | `/api/firebase/csrf` | CSRF 토큰 |
| POST | `/api/firebase/fcm/send` | FCM 전송 |
| POST | `/api/firebase/messages` | Firestore 저장 |
| GET | `/api/firebase/messages/{id}` | Firestore 조회 |
| GET | `/api/firebase/users/{uid}` | Firebase Auth 조회 |

`enabled: true`는 초기화 여부이며 서비스 권한까지 보장하지 않습니다. FCM 토큰은 실제 클라이언트에서 받아야 합니다. 전송 성공과 실제 알림 표시는 구분해서 확인하세요. Firebase UID는 게시판 로그인 아이디와 다릅니다.

## Jar 빌드와 실행

VS Code Gradle 보기에서 `bootJar`를 실행하거나 프로젝트 폴더의 터미널에서 실행합니다.

```powershell
.\gradlew.bat bootJar
```

최종 예제의 출력 파일은 `build/libs/spring-boot-study.jar`입니다. 기존 서버를 중지하고 DB·Firebase 환경 변수가 등록된 터미널에서 실행합니다.

```text
java -jar build/libs/spring-boot-study.jar --spring.profiles.active=dev
```

환경 변수만 바꾸면 재시작하고, Java·HTML·프로젝트 내부 설정 파일을 바꾸면 재빌드 후 실행합니다. Gradle toolchain과 터미널의 Java가 다를 수 있으므로 `java -version`으로 실행 JDK도 확인하세요.

## 개발·운영 설정

| 파일 | 역할 |
| --- | --- |
| `application.properties` | 공통 설정과 기본 Profile |
| `application-dev.properties` | 개발 DB 연결과 스키마 업데이트 |
| `application-prod.properties` | 운영 DB 연결과 스키마 검증 |

운영은 `SPRING_PROFILES_ACTIVE=prod`와 해당 환경의 연결값을 등록합니다. 운영 스키마와 관리자 계정은 미리 준비해야 합니다. Profile 변경으로 데이터가 이전되거나 Firebase가 자동 준비되지는 않습니다.

War·HTTPS·Flyway 등 교재의 추가 설명을 적용할 때는 필요한 코드와 배포 환경을 별도로 준비하세요. 현재 최종 예제의 빌드 설정은 실행 가능한 Jar 기준입니다.

## 자주 묻는 질문

| 증상 | 확인할 내용 |
| --- | --- |
| Gradle 명령을 찾지 못함 | 해당 장의 `spring` 폴더에서 Wrapper 실행 |
| `bootRun`이 계속 실행 중 | 시작 완료 로그가 있으면 요청을 기다리는 정상 상태 |
| 8080 포트 사용 중 | 다른 장 또는 이전 서버 중지 |
| DB 환경 변수 누락 | 실제 실행 프로세스에 변수 전달 여부 확인 |
| 기존 게시글·계정이 안 보임 | DB URL·사용자·Profile 확인 |
| Firebase 키 관련 시작 오류 | 키 설정 또는 `FIREBASE_ENABLED=false` 확인 |
| 일반 사용자의 관리자 접근이 403 | 관리자 권한이 필요한 정상적인 접근 제한 |
| 환경 이름 한글이 깨짐 | properties에 `운영`은 `\uC6B4\uC601`, `개발`은 `\uAC1C\uBC1C`로 표기 |

## 예제 활용과 오류 제보

예제를 변경한 뒤 CRUD, 검색·페이징, 폼 검증, 로그인·로그아웃, 관리자 권한, REST API를 다시 확인하세요. Firebase를 활성화했다면 해당 웹 실습도 확인합니다.

오류 제보 시 GitHub Issues에 **장 폴더, Java 버전, 실행 방법, Profile, 재현 순서와 오류 메시지**를 남겨 주세요. 비밀번호·서비스 계정 키·인증 토큰은 로그와 설정에서 제거하고, 비밀값이 들어간 실행 설정도 커밋하지 않습니다.

이 저장소는 교재 학습용 예제입니다. 실제 서비스에는 운영 계정 관리, HTTPS, 비밀 관리, 테스트와 백업 등을 환경에 맞게 적용해야 합니다.
