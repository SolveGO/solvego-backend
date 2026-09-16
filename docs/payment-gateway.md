# Toss Payments Billing gateway

`PaymentGateway`는 PG 통신만 담당한다. Payment 상태 변경, Subscription 활성화,
Repository 저장, Controller, 갱신 스케줄러는 포함하지 않는다.

## 설정

실행 환경에 `TOSS_PAYMENTS_SECRET_KEY`를 주입한다. `.env.example`은 설정 예시이며,
Spring Boot는 `.env` 파일을 자동으로 읽지 않으므로 IDE/배포 환경에서 환경변수로 전달한다.
키가 비어 있으면 앱은 시작되지만 게이트웨이 호출은 `NOT_CONFIGURED`로 실패한다.

- `TOSS_PAYMENTS_BASE_URL`: 기본 `https://api.tosspayments.com`
- `TOSS_PAYMENTS_CONNECT_TIMEOUT`: 기본 `5s`, 양수 필수
- `TOSS_PAYMENTS_READ_TIMEOUT`: 기본 `65s`, 최소 `60s`

## API 계약

- `issueBillingKey(authKey, customerKey)`: `POST /v1/billing/authorizations/issue`.
  응답에서 billingKey와 customerKey만 반환하고 요청한 customerKey와 일치하는지 확인한다.
- `charge(billingKey, customerKey, orderId, orderName, amount)`:
  `POST /v1/billing/{billingKey}`. 응답의 `totalAmount`를 결과 DTO의 `amount`에,
  `approvedAt`의 시간대 포함 시각을 `Instant`로 변환한다.
  `DONE` 상태, 주문번호, 금액, 승인 시각과 paymentKey를 확인한다.
- 인증은 `Basic base64(secretKey + ":")`를 사용한다.

`PaymentGatewayException`은 reason, HTTP 상태(있는 경우), Toss 오류 코드(있는 경우)를
제공한다. 원본 응답 메시지/본문, 요청 URL, 원인 예외는 보존하지 않는다.
키를 담는 DTO의 `toString()`은 값을 마스킹한다. HTTP wire/body 디버그 로깅은 켜지 않는다.

통신 실패나 잘못된 응답은 실제 결제 실패를 확정하지 않는다. 호출자는 결과를 확인해야 하며,
이 게이트웨이는 자동 재시도하지 않는다. 빌링키의 암호화 저장 역시 호출 계층의 책임이다.

## 검증

외부 API 호출 없이 MockRestServiceServer로 요청/응답 계약을 검증한다.

```sh
./gradlew test --tests '*TossPayment*Test' --tests '*AiClientTest' --tests '*SubscriptionEntitlementServiceTest'
```

2026-09-16 확인한 공식 문서:
- [코어 API: 자동결제](https://docs.tosspayments.com/reference#자동결제)
- [자동결제 결제창 연동: 인증 및 빌링키 발급](https://docs.tosspayments.com/guides/v2/billing/integration)
