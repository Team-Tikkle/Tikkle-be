package com.tikkle.payment.swagger;

import com.tikkle.global.response.ApiResponse;
import com.tikkle.payment.dto.request.PaymentScrapingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment", description = "결제 정보 처리 API")
public interface PaymentSwagger {
    @Operation(summary = "결제 푸시 알림 수신", description = "안드로이드 클라이언트가 스크래핑한 결제 푸시 알림 데이터를 백엔드로 전송합니다.")
    @Parameter(name = "X-Tikkle-Signature", description = "HMAC SHA256 서명값 (Body + Timestamp)", required = true, in = ParameterIn.HEADER)
    @Parameter(name = "X-Tikkle-Timestamp", description = "요청 생성 타임스탬프 (Unix Time - 초 단위)", required = true, in = ParameterIn.HEADER)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수신 성공 (중복/카드불일치/잔돈0원/분류실패 건도 200으로 ActionType을 담아 응답). merchant 필드는 AI 정제된 이름이 반환됩니다.",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "정상 처리 (장중 + 자동)", value = "{ \"code\": \"SUCCESS\", \"message\": \"요청에 성공했습니다.\", \"data\": { \"actionType\": \"ORDER_REQUESTED\", \"merchant\": \"스타벅스\", \"paymentAmount\": 4500, \"spareChange\": 500, \"ticker\": \"005930\", \"stockName\": \"삼성전자\" } }"),
                            @ExampleObject(name = "AI 분류 실패/타임아웃 (조기 종료)", value = "{ \"code\": \"SUCCESS\", \"message\": \"요청에 성공했습니다.\", \"data\": { \"actionType\": \"IGNORE_CLASSIFICATION_FAILED\", \"merchant\": \"스타벅스일산문화센터\", \"paymentAmount\": 4500, \"spareChange\": 0, \"ticker\": null, \"stockName\": null } }")
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (필수 파라미터 누락 등)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"COMMON-001\", \"message\": \"잘못된 요청 파라미터입니다.\" }"
                    ))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "보안 검증 실패 (서명 불일치 또는 타임스탬프 5분 만료)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"PAYMENT-001\", \"message\": \"유효하지 않은 서명입니다.\" }"
                    )))
    })
    ApiResponse<?> receivePaymentScraping(PaymentScrapingRequest request);
}