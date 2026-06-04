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

    @Operation(summary = "결제 푸시 알림 수신", description = "스크래핑한 결제 푸시 알림 데이터를 수신합니다.")
    @Parameter(name = "X-Tikkle-Signature", description = "HMAC SHA256 서명값", required = true, in = ParameterIn.HEADER)
    @Parameter(name = "X-Tikkle-Timestamp", description = "요청 생성 타임스탬프 (Unix Time)", required = true, in = ParameterIn.HEADER)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "수신 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"SUCCESS\", \"message\": \"요청에 성공했습니다.\", \"data\": null }"
                    ))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "보안 검증 실패",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{ \"code\": \"PAYMENT-001\", \"message\": \"유효하지 않은 서명입니다.\" }"
                    )))
    })
    ApiResponse<?> receivePaymentScraping(PaymentScrapingRequest request);
}