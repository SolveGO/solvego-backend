# 최초 PRO 구독 결제 (테스트 키 전용)

## 흐름과 범위

1. 로그인한 FREE 사용자가 MyPage에서 구독 버튼을 누른다.
2. 서버가 사용자별 UUID customerKey와 최초 주문을 준비한다. 금액은 서버 설정의 **월 5,000원**이며 Payment는 READY로 저장된다.
3. 화면에서 금액 확인 후 공식 Toss SDK v2의 `payment({ customerKey }).requestBillingAuth({ method: "CARD", successUrl, failUrl })`를 실행한다.
4. `/billing/return`에서 authKey/customerKey를 인증된 백엔드에 POST한다. URL의 민감한 쿼리는 즉시 브라우저 기록에서 제거하며 브라우저 저장소에는 보관하지 않는다.
5. 서버가 요청을 선점한 뒤 billingKey 발급 → AES-GCM 암호화 저장 → 최초 결제 승인을 순서대로 수행한다.
6. Payment SUCCEEDED와 Subscription PRO/ACTIVE를 원자적으로 반영한다. 승인 시각부터 Asia/Seoul 기준 1개월(월말 보정)이며 nextBillingAt은 기간 종료 시각, autoRenew=true, cancelAtPeriodEnd=false다.
7. MyPage로 이동하여 `/api/users/me`, 기존 AI 사용량 API를 재조회한다. 기존 사용 횟수는 유지되고 일일 한도만 FREE 5에서 PRO 30으로 늘어난다.

자동 갱신 의사/예정 시각만 저장한다. 스케줄러가 없으므로 추가 청구는 발생하지 않는다. 구독 취소·환불·실패 재시도는 구현하지 않았다. 프론트·백엔드 모두 이번 기능에서 test 키만 허용한다.

## 인증된 API

기존 Bearer JWT와 Refresh Cookie 갱신을 그대로 사용한다. 사용자 ID, 결제 금액은 클라이언트 요청에서 받지 않는다.

| Method | Path | 역할 |
| --- | --- | --- |
| POST | `/api/subscriptions/pro/checkouts` | 최초 결제 준비. 기존 주문이 있으면 동일 주문/상태 반환 |
| POST | `/api/subscriptions/pro/checkouts/{orderId}/complete` | `{ "authKey": "...", "customerKey": "..." }`로 완료 |
| GET | `/api/subscriptions/pro/checkouts/{orderId}` | 로그인 사용자의 주문 상태 조회. PG 재호출 없음 |

응답은 orderId, customerKey, orderName, amount, status, currentPeriodEndAt이다. billingKey, 암호문, secret key, 암호화 키는 반환하지 않는다. `Cache-Control: no-store`를 적용한다. 소유자/customerKey 불일치, 설정 미완료 등은 기존 ErrorResponse 형식의 409, 요청 검증 실패는 400, 인증 실패는 401이다.

결제 처리 결과는 HTTP 200의 `status`로 확인한다. **HTTP 200이 곧 결제 성공은 아니다.** 성공은 SUCCEEDED만 해당한다.

## 결제 상태, 중복 방어, 복구 경계

| 상태 | 의미 / 동작 |
| --- | --- |
| READY | 주문만 준비됨. 인증 취소/실패 후 같은 주문으로 카드 인증 시작 가능 |
| PROCESSING | 한 요청이 처리권을 커밋함. 동시 요청/새로고침/재전송은 PG를 호출하지 않고 이 상태 반환 |
| SUCCEEDED | 결제와 PRO 활성화가 DB에 함께 저장됨. 재요청은 같은 결과 반환 |
| FAILED | 빌링 인증의 명확한 오류 또는 카드사 거절 등 확정 실패. 실패 코드/시각 저장, PRO 미활성화 |
| UNKNOWN | 타임아웃, 통신 오류, 잘못된 응답, 모호한 HTTP 오류 또는 승인 후 DB 반영 실패. 실패 시각을 기록하지 않으며 PRO 미활성화 |

구독 row의 PESSIMISTIC_WRITE 잠금으로 준비/선점/종료 처리를 직렬화한다. `V7`의 `(subscription_id, initial_payment_marker)` 고유 제약은 사용자 구독당 INITIAL 한 건만 허용한다. RENEWAL은 marker=NULL로 제한하지 않는다. UUID orderId/customerKey에도 기존 DB 고유 제약이 적용된다. 주문 금액은 READY 생성 시 고정되어 설정 변경이나 프론트 조작으로 바뀌지 않는다.

프론트는 동기 ref 잠금과 버튼 비활성화로 중복 클릭을 막고, React StrictMode의 effect 재실행은 같은 Promise를 공유한다. 반환 페이지 새로고침 후 authKey가 없으면 GET 상태 조회만 한다. JWT 갱신으로 POST가 다시 전송되어도 서버의 선점 기록이 중복 청구를 막는다.

FAILED/UNKNOWN/PROCESSING 주문을 READY로 자동 복구하지 않는다. 실패 재시도 기능이 범위 밖이므로 새 INITIAL 주문도 만들지 않는다. 테스트를 새로 진행하려면 새 FREE 테스트 계정을 사용한다.

### 트랜잭션

orchestration은 NOT_SUPPORTED, DB 작업은 별도 빈의 REQUIRES_NEW다.

- TX1: prepare, READY 저장
- TX2: 소유권 검증 및 READY → PROCESSING 커밋
- 트랜잭션 없이 Toss 빌링키 발급
- TX3: 암호문 저장 커밋 (실패하면 승인 API를 호출하지 않음)
- 트랜잭션 없이 Toss 결제 승인
- TX4: Payment 성공 + Subscription 활성화 원자적 커밋

승인 후 TX4 실패 시 별도 TX로 UNKNOWN을 남긴다. DB 전체 장애로 이 기록도 실패하면 이미 커밋된 PROCESSING이 중복 청구를 차단한다. 프로세스가 외부 호출 도중 종료되어도 같은 방식으로 차단된다. DB 커밋 후 응답만 유실됐다면 조회로 SUCCEEDED를 확인할 수 있다.

이 설계는 Toss와 DB 사이의 분산 원자성을 보장하지 않는다. 결제가 승인됐지만 PRO가 아직 FREE일 수 있다. 장시간 PROCESSING/UNKNOWN은 운영자가 **저장된 orderId로 Toss 테스트 상점/공식 주문 조회 API에서 승인 여부·금액을 확인**해야 한다. 확인 전 재청구/READY 변경/주문 삭제를 하지 않는다. 실제 승인이 확인되면 해당 주문과 구독의 상태·기간을 한 DB 트랜잭션으로 복구해야 한다. 자동 조회/재처리/관리자 복구 API는 이번 범위에 포함하지 않았다.

## 빌링키 보관과 로깅

AES-256-GCM, 암호화마다 무작위 12바이트 nonce, 128비트 인증 태그를 사용한다. AAD에는 버전과 customerKey를 넣어 다른 고객의 암호문으로 바꿔치기할 수 없도록 한다. DB의 billingKeyCiphertext에는 `v1:` + Base64(nonce + ciphertext + tag)만 저장한다. 키는 별도 환경변수로만 주입하고 DB에 저장하지 않는다. 키를 잃거나 교체하면 기존 암호문을 복호화할 수 없으므로 안전하게 보관한다. 키 회전은 후속 범위다.

민감 DTO는 toString을 마스킹하며 예외의 원문 cause/body/URL을 전달하지 않는다. 기존 Hibernate 바인딩 TRACE는 OFF로 바꿨다. HTTP wire/body 로그를 켜지 않는다. 반환 URL에 Toss가 authKey를 붙이므로 프론트 호스팅/CDN 접근 로그는 `/billing/return`의 쿼리 문자열을 수집하지 않도록 설정하고, 분석 도구에 전달하지 않는다. HTML referrer 정책은 no-referrer다.

## 환경변수와 브라우저 테스트 절차

백엔드:

| 변수 | 값 |
| --- | --- |
| `TOSS_PAYMENTS_SECRET_KEY` | Toss API 개별 연동 키의 테스트 secret key (`test_sk_...`) |
| `BILLING_KEY_ENCRYPTION_KEY` | Base64 인코딩한 무작위 32바이트 키 |
| `PRO_MONTHLY_AMOUNT` | `5000` (기본값) |
| `TOSS_PAYMENTS_BASE_URL` | 기본 `https://api.tosspayments.com` |
| `TOSS_PAYMENTS_CONNECT_TIMEOUT` | 기본 `5s` |
| `TOSS_PAYMENTS_READ_TIMEOUT` | 기본 `65s`, 최소 60초 |
| `CORS_ALLOWED_ORIGINS` | 로컬 `http://localhost:5173` |

프론트:

| 변수 | 값 |
| --- | --- |
| `VITE_API_BASE_URL` | `http://localhost:8080` |
| `VITE_TOSS_PAYMENTS_CLIENT_KEY` | 같은 상점의 API 개별 연동 테스트 client key (`test_ck_...`) |

1. Toss 개발자센터에서 Billing용 API 개별 연동 테스트 키 쌍을 확인한다. 결제위젯 키나 live 키는 사용하지 않는다.
2. `openssl rand -base64 32`로 암호화 키를 생성하여 로컬 비밀 설정/환경변수에 저장한다. 실제 키를 예시 파일·소스·Git에 넣지 않는다. 프론트에는 client key만 둔다.
3. MySQL/Redis를 시작한다. 기존 로컬 환경은 `docker compose up -d db redis`를 사용한다. 백엔드 IDE 환경변수 또는 shell 환경에 DB/JWT와 위 결제 설정을 주입한다. `.env`는 Spring Boot가 자동 로드하지 않으므로 IDE/실행 환경에서 전달해야 한다. Docker Compose app에는 추가한 secret/encryption/amount 변수가 전달된다.
4. 백엔드에서 `./gradlew bootRun --args='--spring.profiles.active=local'`을 실행한다. Flyway V7이 적용된다. 프론트 `.env.local`에 두 VITE 변수를 설정한 뒤 `npm install`, `npm run dev -- --host localhost`를 실행한다.
5. `http://localhost:5173`에서 새 FREE 테스트 계정으로 가입/로그인하고 MyPage → 구독 버튼 → **5,000원** 확인 → 카드 등록을 진행한다. 반환 경로는 `/billing/return`이다. 배포형 테스트에서는 이 경로도 SPA index.html로 제공해야 한다.
6. Toss 테스트 결제창을 완료하고 MyPage의 PRO / 일일 30회와 서버 결제 기록을 확인한다. 인증 실패는 청구 전 종료된다. 결과 확인 필요 화면에서는 상태 조회만 사용한다.

테스트 계정/키로만 진행한다. 자동 테스트는 PaymentGateway/SDK를 mock하며 실제 Toss API를 호출하지 않는다. 취소·환불·갱신 스케줄러는 없다.

## 검증 명령과 파일

```sh
# backend: 기존 테스트용 MySQL/Redis 필요
./gradlew test
# frontend
npm run test:run
npm run build
npm run lint
```

주요 변경 파일:
- backend `domain/subscription/billing/{BillingKeyCipher,CheckoutTransactions,InitialSubscriptionService}.java`
- backend `domain/subscription/controller/SubscriptionCheckoutController.java`, `dto/*`, `exception/*`
- backend `domain/payment/entity/Payment.java`, `type/PaymentStatus.java`, `repository/PaymentRepository.java`
- backend `domain/subscription/entity/Subscription.java`, `repository/SubscriptionRepository.java`
- backend `global/exception/GlobalExceptionHandler.java`, `application.yaml`, `.env.example`, `docker-compose.yaml`, `db/migration/V7__initial_subscription_checkout.sql`
- backend `src/test/.../subscription/billing/*`, `subscription/controller/SubscriptionCheckoutControllerTest.java`
- frontend `src/api/subscriptionApi.ts`, `src/billing/tossBilling.ts`, `src/pages/billing/BillingReturnPage.tsx` 및 테스트
- frontend `src/pages/user/MyPage/MyPage.tsx` 및 테스트, `src/App.tsx`, `index.html`, `.env.example`, `package.json`, `package-lock.json`

공식 문서 확인: 2026-09-16.
- [Billing 연동](https://docs.tosspayments.com/guides/v2/billing/integration)
- [SDK v2 초기화와 키 종류](https://docs.tosspayments.com/sdk/v2/js/environment)
- [requestBillingAuth 계약](https://docs.tosspayments.com/sdk/v2/js/payment#paymentrequestbillingauth)
- [Billing 서버 API](https://docs.tosspayments.com/reference#자동결제)

최종 자동 검증 결과: 백엔드 전체 296개 통과(실패/스킵 0), 프론트 전체 97개 통과,
프론트 TypeScript/Vite build 및 ESLint 통과. 자동 테스트는 실제 Toss API를 호출하지 않는다.

2026-09-16 로컬 수동 E2E 검증에서 Toss 테스트 키로 빌링 인증, 카드 등록,
billingKey 발급, 최초 5,000원 테스트 승인, Payment SUCCEEDED, Subscription PRO/ACTIVE,
MyPage의 FREE 5회에서 PRO 30회 전환까지 확인했다. 테스트 키로 수행되어 실제 과금은 없다.
